package com.masoero;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.ribbon.ServerError;
import com.netflix.ribbon.UnsuccessfulResponseException;
import com.netflix.ribbon.http.HttpResponseValidator;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

/** La validacion es llamada para todos los http statuses 5xx.
 * En el caso de 5xx, se dispara directamente el mecanismo de recuperacion de hyxtrix (cache, request, fallback)
 * 
 * @author esteban
 *
 */
public class PersonServiceResponseValidator implements HttpResponseValidator {

	static Logger logger = LoggerFactory.getLogger(PersonServiceResponseValidator.class);

	/* (non-Javadoc)
	 * @see com.netflix.ribbon.ResponseValidator#validate(java.lang.Object)
	 */
	@Override
	public void validate(HttpClientResponse<ByteBuf> response) throws UnsuccessfulResponseException, ServerError {
		HttpResponseStatus status = response.getStatus();
		if (status.code() / 100 != 2) {
			String message = null;
			try {
				if (status.code() / 100 == 4) {
					message = "Unexpected http response: " + status.code();
					throw new UnsuccessfulResponseException(message);
				} else {
					message = "Server error: " + status.code();
					// Respuestas no esperadas se consideran server errors (1xx, 3xx)
					throw new ServerError(message);
				}
			} finally {
				logger.error(message);
			}
		}
	}
}
