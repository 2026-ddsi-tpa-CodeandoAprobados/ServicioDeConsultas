# Servicio de Consultas — DonaTrack

Microservicio de lectura (CQRS) y consumidor de eventos para el sistema **DonaTrack**.

- **Consumidor / Worker RabbitMQ**: Consume asincrónicamente eventos publicados por los cuatro módulos (`Donadores y Entidades`, `Donaciones`, `Logística`, `Incentivos`) a través de un `TopicExchange`.
- **Persistencia NoSQL (MongoDB)**: Construye y actualiza los documentos de lectura combinados.
- **Idempotencia y Consistencia Eventual**: Ignora eventos desordenados u obsoletos comparando marcas temporales (`timestamp` vs `ultimaActualizacion`).
- **Consultas Complejas**: Expone endpoints optimizados para responder las 4 consultas complejas de la consigna sin llamar a los módulos de escritura en tiempo real.
- **Pipeline de Agregación MongoDB**: Resuelve la consulta de "Donadores con más quejas" utilizando un pipeline de agregación (`$match`, `$sort`).

---

## Endpoints de Consulta

| Consulta | Endpoint | Método | Descripción |
| :--- | :--- | :--- | :--- |
| **Consulta 1** | `/consultas/donador/{id}` | `GET` | **Ficha integral de un donador**: estado, categoría actual, historial de categorías, insignias, misión en curso con progreso, donaciones por estado, quejas recibidas y entidades beneficiadas. |
| **Consulta 2** | `/consultas/necesidades-urgentes` | `GET` | **Necesidades urgentes sin cubrir**: lista ordenada por urgencia descendente. Filtros opcionales por query param: `urgenciaMinima`, `tipoNecesidad`, `categoriaProducto`, `subcategoriaProducto`. |
| **Consulta 3** | `/consultas/depositos` | `GET` | **Estado de los depósitos**: ocupación (stock / capacidad), algoritmo de matchmaking, asignaciones por estado, tiempo promedio de entrega y cantidad de entidades distintas atendidas. |
| **Consulta 3** | `/consultas/depositos/{id}` | `GET` | Estado de un depósito particular por ID. |
| **Consulta 4** | `/consultas/calidad-donaciones` | `GET` | **Calidad de lo donado**: proporción de donaciones que terminaron con queja por categoría y subcategoría. Filtro opcional: `categoria`. |
| **Consulta 4 (Pipeline)** | `/consultas/donadores-con-mas-quejas` | `GET` | **Pipeline de Agregación de MongoDB**: lista los donadores con quejas ordenados descendentemente con su estado y categoría. |
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

---

## Ejecución de Tests

```bash
mvn test
```
