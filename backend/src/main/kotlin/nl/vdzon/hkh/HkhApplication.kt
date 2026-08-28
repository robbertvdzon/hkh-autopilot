package nl.vdzon.hkh

import nl.vdzon.hkh.configuration.SecretsEnvLoader
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.modulith.Modulithic
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@Modulithic
@EnableScheduling
class HkhApplication

fun main(args: Array<String>) {
    val application = SpringApplication(HkhApplication::class.java)
    application.setDefaultProperties(SecretsEnvLoader().resolvedValues())
    application.run(*args)
}
