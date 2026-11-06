# Servicio de Consultas — DonaTrack

Microservicio de lectura (CQRS) y consumidor de eventos para el sistema **DonaTrack**.

- **Consumidor / Worker RabbitMQ**: Consume asincrónicamente eventos publicados por los cuatro módulos (`Donadores y Entidades`, `Donaciones`, `Logística`, `Incentivos`) a través de un `TopicExchange`.
- **Persistencia NoSQL (MongoDB)**: Construye y actualiza los documentos de lectura combinados.
- **Idempotencia y Consistencia Eventual**: Ignora eventos desordenados u obsoletos comparando marcas temporales (`timestamp` vs `ultimaActualizacion`).
- **Consultas Complejas**: Expone endpoints optimizados para responder las 4 consultas complejas de la consigna sin llamar a los módulos de escritura en tiempo real.
- **Pipelines de Agregación MongoDB**: La consulta 4 se resuelve con dos pipelines: la calidad de lo donado sobre `donaciones` (`$unwind`, `$group` por donación y por categoría/subcategoría, `$project`, `$sort`) y el ranking de donadores con más quejas sobre `donadores` (`$match`, `$sort`, `$limit`, `$project`).

---

## Endpoints de Consulta

| Consulta | Endpoint | Método | Descripción |
| :--- | :--- | :--- | :--- |
| **Consulta 1** | `/consultas/donador/{id}` | `GET` | **Ficha integral de un donador**: estado, categoría actual, historial de categorías, insignias, misión en curso con progreso, donaciones por estado, quejas recibidas y entidades beneficiadas. |
| **Consulta 2** | `/consultas/necesidades-urgentes` | `GET` | **Necesidades urgentes sin cubrir**: lista ordenada por urgencia descendente. Filtros opcionales por query param: `urgenciaMinima`, `tipoNecesidad`, `categoriaProducto`, `subcategoriaProducto`. |
| **Consulta 3** | `/consultas/depositos` | `GET` | **Estado de los depósitos**: ocupación (stock / capacidad), algoritmo de matchmaking, asignaciones por estado, tiempo promedio de entrega y cantidad de entidades distintas atendidas. |
| **Consulta 3** | `/consultas/depositos/{id}` | `GET` | Estado de un depósito particular por ID. |
| **Consulta 4** | `/consultas/calidad-donaciones` | `GET` | **Calidad de lo donado** (pipeline sobre `donaciones`): proporción de donaciones que terminaron con queja (CONQUEJA) por categoría y subcategoría, de mayor a menor. Filtros opcionales: `categoria`, `subcategoria`. Los productos que llegan sin categoría se informan como `SIN_CATEGORIA` / `general`. |
| **Consulta 4 (Pipeline)** | `/consultas/donadores-con-mas-quejas` | `GET` | **Pipeline de Agregación de MongoDB**: los donadores con más quejas, con `id`, `nombre`, `estado`, `categoria` y `quejasRecibidas`. Parámetro opcional `limite` (por defecto 10, `CONSULTAS_RANKING_LIMITE`). |
| **Mantenimiento Demo** | `/consultas/admin/reset` | `DELETE` | Borra todas las colecciones en MongoDB para inicializar corridas. |
| **Demo Seeder** | `/consultas/admin/seed-demo` | `POST` | Precarga los datos de ejemplo del enunciado (Lucía Fernández, depósitos, necesidades). |

---

## Variables de Entorno

| Variable | Descripción | Valor por Defecto |
| :--- | :--- | :--- |
| `PORT` | Puerto HTTP del servicio | `8085` |
| `MONGODB_URI` | URI de conexión a MongoDB (MongoDB Atlas o local) | `mongodb://localhost:27017/donatrack` |
| `CLOUDAMQP_URL` / `RABBITMQ_URL` | URL AMQP completa de RabbitMQ (e.g. CloudAMQP) | `amqp://localhost:5672` |
| `RABBITMQ_HOST` | Host de RabbitMQ | `localhost` |
| `RABBITMQ_PORT` | Puerto de RabbitMQ | `5672` |
| `RABBITMQ_USERNAME` | Usuario de RabbitMQ | `guest` |
| `RABBITMQ_PASSWORD` | Contraseña de RabbitMQ | `guest` |
| `RABBITMQ_EXCHANGE` | Exchange de eventos | `donatrack.exchange` |
| `RABBITMQ_QUEUE` | Cola consumida por este worker | `consultas.mongo.queue` |
| `RABBITMQ_ROUTING_KEY` | Patrón de routing keys a consumir | `#` (todos los eventos) |
| `CONSULTAS_RANKING_LIMITE` | Cuántos donadores devuelve `/consultas/donadores-con-mas-quejas` sin `?limite=` | `10` |
| `DD_ENABLED` | Exporta las métricas a Datadog | `false` |
| `DD_API_KEY` | Clave de la API de Datadog. **Nunca** se escribe en el repo: solo como variable de entorno (en Render, como secreto). | *(vacía)* |
| `DD_URI` | URL de la API de Datadog (otra región, p. ej. `https://api.us5.datadoghq.com`) | `https://api.datadoghq.com` |
| `DD_STEP` | Cada cuánto se envían las métricas | `30s` |

---

## Métricas

Micrometer con el registro de Datadog. Todas las métricas llevan la etiqueta `application:consultas`.

| Métrica | Etiquetas | Qué cuenta |
| :--- | :--- | :--- |
| `consultas.eventos.recibidos` | `tipo` | Cada evento que llega a la cola. |
| `consultas.eventos.procesados` | `tipo` | Eventos que modificaron el modelo de lectura. |
| `consultas.eventos.ignorados` | `tipo`, `motivo` | Eventos que no cambiaron nada: `repetido` (mismo id de evento o misma queja), `obsoleto` (más viejo que lo guardado), `no_mapeado`, `catalogo` (insignias y misiones de Incentivos), `invalido`. |
| `consultas.eventos.errores` | `tipo` | Excepciones al procesar un evento (el evento se descarta igual que antes). |
| `consultas.requests` | `endpoint`, `metodo` | Pedidos a cada endpoint de `/consultas/**`, por patrón de ruta. |

`tipo` toma solo los tipos que publican los módulos; cualquier otro se informa como `OTRO`. Para enviarlas: `DD_ENABLED=true` y `DD_API_KEY=<clave>`. Activo y sin clave, la app arranca igual pero los envíos fallan.

---

## Ejecución de Tests

```bash
mvn test
```
