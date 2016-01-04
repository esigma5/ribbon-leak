package com.masoero;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.ribbon.hystrix.FallbackHandler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import rx.Observable;

public class PersonServiceFallbackHandler implements FallbackHandler<ByteBuf> {

	static final ObjectMapper mapper = new ObjectMapper();
	
	/* (non-Javadoc)
	 * @see com.netflix.ribbon.hystrix.FallbackHandler#getFallback(com.netflix.hystrix.HystrixInvokableInfo, java.util.Map)
	 */
	@Override
	public Observable<ByteBuf> getFallback(HystrixInvokableInfo<?> hystrixInfo, Map<String, Object> requestProperties) {
		// Actualmente este método debe devolver siempre ByteBuff
		Response response = new Response("fail");
		ByteBuf byteBuf = null;
		try {
			byte[] bytes = mapper.writeValueAsBytes(response);
			byteBuf = UnpooledByteBufAllocator.DEFAULT.buffer(bytes.length);
//			byteBuf = UnpooledByteBufAllocator.DEFAULT.heapBuffer(bytes.length);
//			byteBuf = PooledByteBufAllocator.DEFAULT.buffer(bytes.length);
			byteBuf.writeBytes(bytes);
			return Observable.just(byteBuf);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Could not write response", e);
		}
		
	}

}
