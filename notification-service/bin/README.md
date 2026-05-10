# notification-service

Microservice Spring Boot indépendant pour notifications async (RabbitMQ + Email).

## Rôle

- Consommer les événements email publiés par `gestion-demande`
- Envoyer l'email (ou logger en local si `app.mail.enabled=false`)
- Ne pas stocker les notifications en base (stateless)

## Prérequis

- Java 17
- RabbitMQ actif (ex via `docker-compose.yaml` racine `uib-bnpl`)

## Configuration

`src/main/resources/application.properties` :

- `server.port=8082`
- Rabbit:
  - `spring.rabbitmq.host=localhost`
  - `spring.rabbitmq.port=5672`
  - `spring.rabbitmq.username=bnpl`
  - `spring.rabbitmq.password=bnpl123`
- Queue/Exchange:
  - `app.rabbit.notification.exchange=notification.exchange`
  - `app.rabbit.notification.queue=notification.email.send`
  - `app.rabbit.notification.routing-key=notification.email.request`
- Sécurité inter-micro:
  - `internal.api.key=...` (doit être identique à `gestion-demande`)
- Email:
  - `app.mail.enabled=false` (dev local, logs seulement)
  - `MAIL_USERNAME`, `MAIL_PASSWORD` via variables d'environnement pour envoi réel

## Lancement

```bash
mvn spring-boot:run
```

## Health

- `GET http://localhost:8082/api/health`

