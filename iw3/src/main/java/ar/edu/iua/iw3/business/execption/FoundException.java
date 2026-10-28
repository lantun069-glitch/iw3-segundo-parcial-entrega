package ar.edu.iua.iw3.business.execption;

import lombok.Builder;
import lombok.NoArgsConstructor;
@NoArgsConstructor
public class FoundException extends Exception {

	@Builder
	public FoundException(String message, Throwable ex) {
		super(message, ex);
	}
	@Builder
	public FoundException(String message) {
		super(message);
	}
	@Builder
	public FoundException(Throwable ex) {
		super(ex);
	}

	
	
}
