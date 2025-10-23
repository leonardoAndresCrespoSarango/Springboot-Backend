docker-compose build
```

### 3. Iniciar los Servicios

```bash
docker-compose up -d
```

### 4. Ver Logs

```bash
# Todos los servicios
docker-compose logs -f

# Servicio específico
docker-compose logs -f user-service
docker-compose logs -f second-stack-service
```

### 5. Detener los Servicios

```bash
docker-compose down
```

## 📦 Comandos Útiles

### Reconstruir y Reiniciar

```bash
docker-compose up -d --build
```

### Ver Estado de los Servicios

```bash
docker-compose ps
```

### Ejecutar Comandos en un Contenedor

```bash
# Acceder al shell del contenedor
docker-compose exec user-service sh

# Ver logs de la aplicación Java
docker-compose exec user-service cat /app/logs/application.log
```

### Limpiar Todo (Contenedores, Volúmenes, Redes)

```bash
docker-compose down -v
docker system prune -a
```

## 🔧 Configuración Avanzada

### Cambiar Límites de Memoria

Edita `docker-compose.yml` y modifica la variable `JAVA_OPTS`:

```yaml
environment:
  - JAVA_OPTS=-Xmx1024m -Xms512m  # 1GB max, 512MB inicial
```

### Agregar Spring Boot Actuator (Recomendado)

Para mejorar los health checks, agrega a tus `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Y en `application.properties`:

```properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=always
```

### Modo de Desarrollo (Hot Reload)

Para desarrollo local con hot reload, puedes montar el código fuente:

```yaml
volumes:
  - ./user/src:/app/src:ro
```

## 🌐 Puertos Expuestos

- **8002**: User Service (Autenticación, Usuarios, TOTP)
- **8003**: Second Stack Service (Auditoría, Base de datos)

## 🔒 Seguridad

1. **Nunca commites el archivo `.env`** con credenciales reales
2. Los contenedores corren con un usuario no-root (`spring`)
3. Las credenciales de Firebase se montan como solo lectura (`:ro`)
4. Usa secrets de Docker en producción:

```bash
echo "mi-secreto" | docker secret create jwt_secret -
```

## 🐛 Troubleshooting

### Los servicios no se comunican entre sí

Verifica que uses los nombres de servicio en las URLs:
- `http://user-service:8002`
- `http://second-stack-service:8003`

### Error de conexión a Firebase

Verifica que el archivo `firebase-service-account.json` exista en:
`./user/src/main/resources/firebase-service-account.json`

### Error de base de datos

Verifica la URL de Supabase en `.env`:
```
SUPABASE_JDBC_URL=jdbc:postgresql://host:port/database?user=xxx&password=xxx
```

### Contenedor se reinicia constantemente

```bash
# Ver logs detallados
docker-compose logs --tail=100 nombre-servicio

# Verificar health check
docker inspect etikos-user-service | grep -A 10 Health
```

## 📊 Monitoreo

### Ver Recursos Utilizados

```bash
docker stats
```

### Health Checks

Los servicios tienen health checks configurados. Verifica el estado:

```bash
curl http://localhost:8002/actuator/health
curl http://localhost:8003/actuator/health
```

## 🚢 Deployment en Producción

### 1. Build Multi-Arch (para ARM/AMD)

```bash
docker buildx build --platform linux/amd64,linux/arm64 -t etikos-user:latest ./user
```

### 2. Usar Docker Registry

```bash
# Tag
docker tag etikos-user:latest registry.ejemplo.com/etikos-user:latest

# Push
docker push registry.ejemplo.com/etikos-user:latest
```

### 3. Variables de Entorno en Producción

Usa un servicio de secrets management:
- AWS Secrets Manager
- HashiCorp Vault
- Docker Secrets (Swarm)
- Kubernetes Secrets

## 📝 Notas Adicionales

- Las imágenes usan Alpine Linux para reducir tamaño (~150MB por servicio)
- Build multi-etapa para optimizar tamaño final
- Los logs se persisten en el volumen `./logs`
- La red `etikos-backend-network` permite comunicación entre servicios

## 🔄 Actualización de la Aplicación

```bash
# 1. Pull últimos cambios
git pull

# 2. Rebuild sin cache
docker-compose build --no-cache

# 3. Recrear contenedores
docker-compose up -d --force-recreate
```
# Etapa 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copiar archivos de configuración de Maven
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Descargar dependencias (cache layer)
RUN mvn dependency:go-offline -B

# Copiar código fuente
COPY src ./src

# Construir la aplicación
RUN mvn clean package -DskipTests

# Etapa 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Crear usuario no-root para seguridad
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copiar el JAR desde la etapa de build
COPY --from=build /app/target/user-0.0.1-SNAPSHOT.jar app.jar

# Exponer el puerto
EXPOSE 8002

# Variables de entorno por defecto (pueden ser sobrescritas por docker-compose)
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Ejecutar la aplicación
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

