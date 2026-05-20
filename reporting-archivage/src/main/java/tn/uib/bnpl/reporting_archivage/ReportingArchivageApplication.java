package tn.uib.bnpl.reporting_archivage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class ReportingArchivageApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingArchivageApplication.class, args);
    }
}
