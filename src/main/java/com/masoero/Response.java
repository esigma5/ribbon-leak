package com.masoero;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Response {
	@JsonProperty
	private String status;
	
	public Response() {
	}

	public Response(@JsonProperty String status) {
		this.status = status;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

}
