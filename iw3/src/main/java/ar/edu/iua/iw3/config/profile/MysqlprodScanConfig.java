package ar.edu.iua.iw3.config.profile;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Sin ejemplos de integración por cliente en esta entrega: ya no hace
// falta excluir paquetes del escaneo de repositorios. Se deja explícito
// igual (en vez de delegar en el autoescaneo por defecto de Spring Boot)
// para fijar qué paquetes de entidades forman parte de esta versión.
@Configuration
@EnableJpaRepositories(basePackages = "ar.edu.iua.iw3")
@EntityScan(basePackages = {
		"ar.edu.iua.iw3.auth",
		"ar.edu.iua.iw3.ordenes.model"
})
@Profile("mysqlprod")
public class MysqlprodScanConfig {

}
