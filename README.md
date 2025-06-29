# id — Authentication Server

**id** is a secure and extensible authentication server developed by **Jasmine Software Solutions, LLC**. It provides core identity services for modern applications.

## Features

- **Password Authentication**  
  Secure login using modern password hashing (Argon2) with configurable parameters.

- **TOTP MFA**  
  Optional time-based one-time password support for enhanced security.

- **OAuth2 Authorization Server**  
  Let users sign in to your app using their ID account via standard OAuth2 flows.

- **Email Support**  
  Configurable SMTP integration for email-based account verification.

## Environment Configuration

ID uses a `.env` file for configuration via [cdimascio/dotenv-kotlin](https://github.com/cdimascio/dotenv-kotlin). Default values are provided when not specified.

### Core Settings

| Variable                   | Description                                     | Default                                 |
|---------------------------|-------------------------------------------------|-----------------------------------------|
| `PORT`                    | Server port                                     | `80`                                    |
| `DATABASE_DRIVER`         | JDBC driver                                     | `org.sqlite.JDBC`                       |
| `DATABASE_URL`            | JDBC connection string                          | `jdbc:sqlite:file:test?mode=memory&cache=shared` |
| `DATABASE_USERNAME`       | DB username                                     | `""`                                    |
| `DATABASE_PASSWORD`       | DB password                                     | `""`                                    |
| `DATABASE_POOL_SIZE`      | Connection pool size                            | `1`                                     |

### Password Hashing

| Variable                   | Description                                     | Default |
|---------------------------|-------------------------------------------------|---------|
| `PASSWORD_SALT_LENGTH`    | Salt length for password hashing                | `32`    |
| `PASSWORD_HASH_ITERATIONS`| Number of hash iterations                       | `2`     |
| `ARGON2_MEMORY`           | Memory cost for Argon2                          | `65535` |
| `ARGON2_PARALLELISM`      | Threads for Argon2                              | `1`     |

### Templating & Development

| Variable                   | Description                                     | Default |
|---------------------------|-------------------------------------------------|---------|
| `HOT_RELOAD_JTE_TEMPLATES`| Reload JTE templates on changes                 | `false` |

### Email (SMTP)

| Variable               | Description                | Default                  |
|------------------------|----------------------------|--------------------------|
| `SMTP_ENABLED`         | Enable SMTP communication  | `false`                  |
| `SMTP_AUTH`            | Enable SMTP authentication | `true`                   |
| `SMTP_STARTTLS_ENABLE` | Use STARTTLS               | `true`                   |
| `SMTP_HOST`            | SMTP server host           | *none*                   |
| `SMTP_PORT`            | SMTP server port           | `25`                     |
| `SMTP_SSL_TRUST`       | SSL trust config           | *none*                   |
| `SMTP_USERNAME`        | SMTP auth username         | *none*                   |
| `SMTP_PASSWORD`        | SMTP auth password         | *none*                   |
| `SMTP_EMAIL`           | Default sender email       | *none*                   |
| `SUPPORT_EMAIL`        | Contact/support email      | Defaults to `SMTP_EMAIL` |

### Encryption

| Variable                     | Description                        | Default |
|-----------------------------|------------------------------------|---------|
| `ENCRYPTED_PARAMETER_SECRET`| Secret for encrypting parameters   | `""`    |
| `ENCRYPTED_PARAMETER_SALT`  | Salt for encrypting parameters     | `""`    |

### Security Configuration

| Variable                        | Description                                            | Default |
|---------------------------------|--------------------------------------------------------|---------|
| `SESSION_ACCESS_TOKEN_LIFETIME` | Maximum lifetime of a session access token, in seconds | `300`   |

### Testing

| Variable         | Description                         | Default                      |
|------------------|-------------------------------------|------------------------------|
| `TEST_URL`        | Base URL for integration tests      | `http://localhost:<PORT>`    |
| `TEST_HEADLESS`   | Run browser tests headlessly        | `true`                       |
| `LOGIN_TIMEOUT`   | Login timeout in ms                 | `1000.0`                     |

## License

© Jasmine Software Solutions, LLC. All rights reserved.

## Disclaimer

**id** is provided by Jasmine Software Solutions, LLC “as is” and **without any warranty** of any kind, express or implied. This includes, but is not limited to, warranties of merchantability, fitness for a particular purpose, and non-infringement. Use of this software is at your own risk.

While **all rights are reserved**, Jasmine Software Solutions, LLC does not guarantee the reliability, security, or suitability of this software for any particular application or deployment.

By using **id**, you agree that Jasmine Software Solutions, LLC shall not be held liable for any damages arising from its use.