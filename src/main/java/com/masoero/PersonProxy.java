package com.masoero;

import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.proxy.annotation.Http;
import com.netflix.ribbon.proxy.annotation.Http.HttpMethod;
import com.netflix.ribbon.proxy.annotation.Hystrix;

import io.netty.buffer.ByteBuf;

public interface PersonProxy {
	@Http(method = HttpMethod.GET, uri = "/person")
	@Hystrix(validator = PersonServiceResponseValidator.class)
	RibbonRequest<ByteBuf> getOk();
	
	@Http(method = HttpMethod.GET, uri = "/personFail")
	@Hystrix(fallbackHandler = UnPooledAllocatorHeapFallbackHandler.class, validator = PersonServiceResponseValidator.class)
	RibbonRequest<ByteBuf> getFallbackWithHeapAllocator();
	
	@Http(method = HttpMethod.GET, uri = "/personFail")
	@Hystrix(fallbackHandler = UnPooledAllocatorFallbackHandler.class, validator = PersonServiceResponseValidator.class)
	RibbonRequest<ByteBuf> getFallbackWithUnPooledDirectAllocator();
}
