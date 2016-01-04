package com.masoero;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.reactivex.netty.channel.ContentTransformer;

public class PersonaServiceTransformer implements ContentTransformer<Person> {

	static final ObjectMapper mapper = new ObjectMapper();

	@Override
	public ByteBuf call(Person person, ByteBufAllocator byteBufAllocator) {
		try {
			byte[] bytes = mapper.writeValueAsBytes(person);
			ByteBuf byteBuf = byteBufAllocator.buffer(bytes.length);
			byteBuf.writeBytes(bytes);
			return byteBuf;
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Could not write person", e);
		}
	}
	
	public static ObjectMapper getMapper() {
		return mapper;
	}

}
