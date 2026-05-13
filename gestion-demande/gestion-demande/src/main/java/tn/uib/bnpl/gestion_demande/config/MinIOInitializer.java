package tn.uib.bnpl.gestion_demande.config;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MinIOInitializer implements CommandLineRunner {

    private final MinioClient minioClient;
    private final MinioProperties props;

    public MinIOInitializer(MinioClient minioClient, MinioProperties props) {
        this.minioClient = minioClient;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        String bucketName = props.bucket();
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );

            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                System.out.println("MinIO bucket '" + bucketName + "' created successfully");
            } else {
                System.out.println("MinIO bucket '" + bucketName + "' already exists");
            }
        } catch (Exception e) {
            System.err.println("Error initializing MinIO bucket: " + e.getMessage());
        }
    }
}