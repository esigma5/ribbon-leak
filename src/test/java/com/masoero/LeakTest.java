package com.masoero;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.netflix.config.ConfigurationManager;
import com.netflix.ribbon.Ribbon;

import io.netty.buffer.ByteBuf;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;
import io.reactivex.netty.protocol.http.UnicastContentSubject;
import rx.Observable;
import rx.internal.operators.BufferUntilSubscriber;
import rx.subjects.ReplaySubject;

/** Normal response: 
 * - Llega con refCnt:2, es liberado por ribbon siempre y cuando se ejecute toObservable(). Sino, hay que agregar un release.
 * 
 * Fallback response:
 * - Llega con refCnt:1, no lo libera nadie. Hay que liberarlo manualente si es direct buffer, o usar un heap buffer.
 * 
 * @author esteban
 *
 */
public class LeakTest {
	
	static Logger logger = LoggerFactory.getLogger(LeakTest.class);

	private ObjectMapper mapper = new ObjectMapper();
	
	@Rule
	public WireMockRule wireMockRule = new WireMockRule(8081);	
	
	@Before
	public void before(){
		stubFor(get(urlEqualTo("/person"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"status\":\"success\"}")));
		
		stubFor(get(urlEqualTo("/personFail"))
				.willReturn(aResponse()
						.withStatus(500)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"status\":\"neverToReadBody\"}")));
	}
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		
		ConfigurationManager.loadPropertiesFromResources("basic-config.properties");
		
		ResourceLeakDetector.setLevel(Level.PARANOID);
		
	}

	@Test
	public void getBlockingWithOkResponse() {
		PersonProxy proxy = Ribbon.from(PersonProxy.class);
		for (int i = 0; i < 10; i++) {
			logger.info("Calling: " + i);
			
			ByteBuf byteBuff = proxy.getOk().execute();
			
			Response response = parseResponseAndReleaseBuffer(byteBuff, 1);
			
			assertEquals(response.getStatus(), "success");			
		}
	}

	@Test
	public void getBlockingWith500ResponseAndFallbackWithUnPooledAllocatorHeapFallbackHandler() {
		PersonProxy proxy = Ribbon.from(PersonProxy.class);
		for (int i = 0; i < 10; i++) {
			
			ByteBuf byteBuff = proxy.getFallbackWithHeapAllocator().execute();
			
			Response response = parseResponseAndReleaseBuffer(byteBuff);
			
			assertEquals(response.getStatus(), "fail");			
		}
	}
	
	private void sleep(int millis) {
		try {
			Thread.sleep(0);
		} catch (InterruptedException e) {
		}
	}
	
	@Test
	public void getNonBlockingWithToObservableWithOkResponseThenParsingWithoutReleasingThenBlocking() {
		PersonProxy proxy = Ribbon.from(PersonProxy.class);
		for (int i = 0; i < 1; i++) {
			logger.info("Calling: " + i);
			Observable<Response> response = proxy.getOk().toObservable().map((buf) -> parseResponseAndReleaseBuffer(buf, 0));
			response.toBlocking().last();
		}
		sleep(1000);
	}
	
	@Test
	public void getNonBlockingWithObserveWithOkResponseThenParsingWithoutReleasingThenBlocking() {
		PersonProxy proxy = Ribbon.from(PersonProxy.class);
		for (int i = 0; i < 10; i++) {
			logger.info("Calling: " + i);
			Observable<Response> response = proxy.getOk().observe().map((buf) -> parseResponseAndReleaseBuffer(buf, 1));
			response.toBlocking().last();
		}
		sleep(1000);
	}
	
	@Test
	public void getNonBlockingWithOkResponseThenParsingWithoutReleasingWithAsyncSubscription() {
		PersonProxy proxy = Ribbon.from(PersonProxy.class);
		for (int i = 0; i < 1; i++) {
			logger.info("Calling: " + i);
			Observable<Response> response = proxy.getOk().toObservable().map((buf) -> parseResponseAndReleaseBuffer(buf, 0));
			ReplaySubject<Response> subject = ReplaySubject.create();
			response.subscribe(subject);
		}
		logger.info(" === End calls ==== ");
		sleep(1000);
	}
	
	@Test
	public void getNonBlockingWithOkResponseWithoutParsingWithoutReleaseForgetting() {
		PersonProxy proxy = Ribbon.from(PersonProxy.class);
		for (int i = 0; i < 100; i++) {
			logger.info("Calling: " + i);
			proxy.getOk().observe();
		}
		logger.info(" === End calls ==== ");
		sleep(1000);
	}
	
	@Test
	public void getNonBlockingWith500ResponseAndFallbackWithPooledAllocator() {
		PersonProxy proxy = Ribbon.from(PersonProxy.class);
		for (int i = 0; i < 100; i++) {
			logger.info("Calling: " + i);
			Observable<Response> byteBuff = proxy.getFallbackWithUnPooledDirectAllocator().toObservable().map((buf) -> parseResponseAndReleaseBuffer(buf, 1));
			ReplaySubject<Object> subject = ReplaySubject.create();
			byteBuff.subscribe(subject);
		}
		logger.info(" === End calls ==== ");
		sleep(5000);
	}
	
	@Test
	public void getNonBlockingWith500ResponseAndFallbackWithheapAllocator() {
		PersonProxy proxy = Ribbon.from(PersonProxy.class);
		for (int i = 0; i < 100; i++) {
			logger.info("Calling: " + i);
			Observable<Response> byteBuff = proxy.getFallbackWithHeapAllocator().toObservable().map((buf) -> parseResponseAndReleaseBuffer(buf, 0));
			ReplaySubject<Object> subject = ReplaySubject.create();
			byteBuff.subscribe(subject);
		}
		logger.info(" === End calls ==== ");
		sleep(1000);
	}
	
	
	private Response parseResponseAndReleaseBuffer(ByteBuf byteBuff) {
		return this.parseResponseAndReleaseBuffer(byteBuff, 1);
	}
	
	private Response parseResponseAndReleaseBuffer(ByteBuf byteBuff, int timesToRelease) {
		try {
			String stringBytes = byteBuff.toString(Charset.defaultCharset());
			return mapper.readValue(stringBytes, Response.class);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			logger.info("byteBuff Refcount:" + byteBuff.refCnt());
			if (byteBuff.isDirect() && timesToRelease > 0){
				byteBuff.release(timesToRelease);
				logger.info("byteBuff released {} times (isDirect:{}). Refcount: {}", timesToRelease, byteBuff.isDirect(), byteBuff.refCnt());				
			} else {
				logger.info("byteBuff NOT released {} times (isDirect:{}). Refcount: {}", timesToRelease, byteBuff.isDirect(), byteBuff.refCnt());
			}
		}
	}

}
