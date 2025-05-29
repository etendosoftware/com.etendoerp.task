# Etendo ERP – Task-Type Workflow Extension

This extension adds **rule-based task creation**, **state transitions**, and **Kafka messaging** capabilities to Etendo ERP.

## 🧩 What `TaskTypeMatchJob` Does

```
Debezium Event ──► TaskTypeMatchJob ──► ETASK_Task + Kafka Topics
                      (rules • filters • states)
```

### Stages & Behavior

| Stage                     | Behavior                                                                                                                                                      |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **1. Normalise**          | Extracts table, verb (create / update / delete), and before/after JSON.                                                                                       |
| **2. Table `ETASK_Task`** | • `Create` (created_automatically = N) → triggers events of the initial state. <br> • `Update` (status changes) → triggers events of the new state.           |
| **3. Other Tables**       | For each matching Task Type:<br>• Pass JEXL filter + advanced logic.<br>• Create task (stores full JSON in `event_jsoninfo`).<br>• Fire initial state events. |
| **4. Output**             | Returns JSON: <br>`json { "next": ["topicA", …], "message": {…}, "tasks": [{"task": "…", "state": "…"}, …] }`                                                 |

## 🧱 Required Modules / Branches

| Gradle Module                | Branch              |
| ---------------------------- | ------------------- |
| `com.etendoerp.asyncprocess` | `epic/ETP-1200-Y24` |
| `com.etendoerp.etendorx`     | `epic/ETP-1200-Y24` |
| `com.etendoerp.reactor`      | `release/24.4`      |

## 🗃️ Database Prerequisites

```sql
ALTER SYSTEM SET wal_level = logical;        -- restart PostgreSQL
ALTER TABLE etask_task REPLICA IDENTITY FULL;  -- enables BEFORE image
```

## 🔌 Register the Debezium Connector

```bash
curl -X POST http://localhost:8083/connectors \
  -H 'Content-Type: application/json' \
  -d '{
        "name": "default",
        "config": {
          "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
          "topic.prefix":   "default",

          "database.hostname": "db",
          "database.dbname":   "etendo",
          "database.user":     "postgres",
          "database.password": "syspass",

          "plugin.name": "pgoutput",

          "table.include.list": "public.c_order,public.etask_task",

          "key.converter":   "org.apache.kafka.connect.json.JsonConverter",
          "value.converter": "org.apache.kafka.connect.json.JsonConverter",
          "key.converter.schemas.enable":   "false",
          "value.converter.schemas.enable": "false"
        }
      }'
```

> ℹ️ Add any extra business table to `table.include.list` as needed.  
> `etask_task` is **mandatory**.

## ⚙️ Build & Deploy

```bash
./gradlew update.database compile.complete smartbuild
# Then start Tomcat
```

## ⚡ Async Job Setup (Window: Async Process)

1. Set **Is Async** = `Y`
2. Set **Topics are RegExp** = `Y`
3. Save
4. **Restart Tomcat** whenever async jobs are added or changed (regenerates Kafka listeners)

## 🧪 Quick Recipe: Add a New Workflow

1. **Task Type**
   - Select Table and Event (`INSERT` / `UPDATE` / `DELETE`)
   - Optional: JEXL Filter + Advanced Logic (Action)
2. **States**
   - Define states in order (via Sequence No)
   - Link each to a `Status`
3. **Events (Child of State)**

   - Set Sequence No
   - Reference a `Jobs Job` (async process)
   - The `Action` must return:

   ```json
   { "topic": "my.kafka.topic" }
   ```

   This topic is collected by `TaskTypeMatchJob` and added to the `next` array in the output.

## ✅ Result

Once configured, each matching DB change will:

- Create a task
- Move it through defined states
- Notify other async jobs via **Kafka**
