package ar.edu.iua.iw3.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

/**
 * Prueba unitaria aislada (sin contexto de Spring, sin base de datos ni correo):
 * verifica la logica de firma/verificacion JWT y la validacion de arranque de
 * SecurityConfiguration, usando claves temporales generadas en memoria para el test.
 */
class JwtSecurityVerificationTest {

	private String randomTempSecret() {
		return "test-secret-" + UUID.randomUUID();
	}

	@Test
	void signAndVerifyWithCorrectSecretSucceeds() {
		String secret = randomTempSecret();
		String token = JWT.create().withSubject("usuario-de-prueba")
				.withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
				.sign(Algorithm.HMAC512(secret.getBytes()));

		DecodedJWT decoded = JWT.require(Algorithm.HMAC512(secret.getBytes())).build().verify(token);

		assertEquals("usuario-de-prueba", decoded.getSubject());
	}

	@Test
	void verifyFailsWithWrongSecret() {
		String signingSecret = randomTempSecret();
		String differentSecret = randomTempSecret();
		String token = JWT.create().withSubject("usuario-de-prueba")
				.withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
				.sign(Algorithm.HMAC512(signingSecret.getBytes()));

		assertThrows(JWTVerificationException.class,
				() -> JWT.require(Algorithm.HMAC512(differentSecret.getBytes())).build().verify(token));
	}

	@Test
	void startupValidationRejectsNullSecret() throws Exception {
		SecurityConfiguration config = new SecurityConfiguration();
		setJwtSecret(config, null);
		assertThrows(IllegalStateException.class, config::validateJwtSecret);
	}

	@Test
	void startupValidationRejectsEmptySecret() throws Exception {
		SecurityConfiguration config = new SecurityConfiguration();
		setJwtSecret(config, "");
		assertThrows(IllegalStateException.class, config::validateJwtSecret);
	}

	@Test
	void startupValidationRejectsBlankSecret() throws Exception {
		SecurityConfiguration config = new SecurityConfiguration();
		setJwtSecret(config, "   ");
		assertThrows(IllegalStateException.class, config::validateJwtSecret);
	}

	@Test
	void startupValidationAcceptsNonBlankSecret() throws Exception {
		SecurityConfiguration config = new SecurityConfiguration();
		setJwtSecret(config, randomTempSecret());
		config.validateJwtSecret();
	}

	private void setJwtSecret(SecurityConfiguration config, String value) throws Exception {
		Field field = SecurityConfiguration.class.getDeclaredField("jwtSecret");
		field.setAccessible(true);
		field.set(config, value);
	}
}
