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

import org.junit.BeforeClass;
import org.junit.ClassRule;
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
import rx.subjects.ReplaySubject;

public class LeakTest {
	
	static Logger logger = LoggerFactory.getLogger(LeakTest.class);

	private ObjectMapper mapper = new ObjectMapper();
	
	@ClassRule
	public static WireMockRule wireMockRule = new WireMockRule(8081);	
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		
		ConfigurationManager.loadPropertiesFromResources("basic-config.properties");
		
		ResourceLeakDetector.setLevel(Level.PARANOID);
		
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

	@Test
	public void getBlockingWithOkResponse() {
		PersonProxy proxy = Ribbon.from(PersonProxy.class);
		for (int i = 0; i < 10; i++) {
			logger.info("Calling: " + i);
//			sleep(500);
			ByteBuf byteBuff = proxy.getOk().execute();
			
			Response response = parseResponse(byteBuff);
			
			assertEquals(response.getStatus(), "success");			
		}
	}

	@Test
	public void getBlockingWith500ResponseAndFallback() {
		PersonProxy proxy = Ribbon.from(PersonProxy.class);
		for (int i = 0; i < 10; i++) {
			
			ByteBuf byteBuff = proxy.getFallback().execute();
			
			Response response = parseResponse(byteBuff);
			
			assertEquals(response.getStatus(), "fail");			
		}
	}
	
	private void sleep(int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
		}
	}
	
	@Test
	public void getNonBlockingWithOkResponse() {
		PersonProxy proxy = Ribbon.from(PersonProxy.class);
//		Collection<UnicastContentSubject<Response>> responses = new LinkedList<>();
		for (int i = 0; i < 1000; i++) {
			logger.info("Calling: " + i);
//			proxy.getOk().observe();
			Observable<Response> byteBuff = proxy.getOk().toObservable().map(this::parseResponse);
//			UnicastContentSubject<Response> subject = UnicastContentSubject.createWithoutNoSubscriptionTimeout();
			ReplaySubject<Object> subject = ReplaySubject.create();
			byteBuff.subscribe(subject);
//			subject.disposeIfNotSubscribed();			
//			responses.add(subject);
		}
//		for (UnicastContentSubject<Response> subject : responses) {
//		}
	}
	
	@Test
	public void getNonBlockingWith500ResponseAndFallback() {
		PersonProxy proxy = Ribbon.from(PersonProxy.class);
//		Collection<UnicastContentSubject<Response>> responses = new LinkedList<>();
		for (int i = 0; i < 1000; i++) {
			logger.info("Calling: " + i);
//			proxy.getOk().observe();
			Observable<Response> byteBuff = proxy.getFallback().toObservable().map(this::parseResponse);
//			UnicastContentSubject<Response> subject = UnicastContentSubject.createWithoutNoSubscriptionTimeout();
			ReplaySubject<Object> subject = ReplaySubject.create();
			byteBuff.subscribe(subject);
//			subject.disposeIfNotSubscribed();			
//			responses.add(subject);
		}
//		for (UnicastContentSubject<Response> subject : responses) {
//		}
	}
	
	
	private Response parseResponse(ByteBuf byteBuff) {
		try {
			String stringBytes = byteBuff.toString(Charset.defaultCharset());
			return mapper.readValue(stringBytes, Response.class);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			byteBuff.release();
			logger.info("byteBuff released. Refcount:" + byteBuff.refCnt());
//			logger.info("byteBuff Refcount:" + byteBuff.refCnt());
		}
	}

}
