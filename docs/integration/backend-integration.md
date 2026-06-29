# Backend Integration Guide

This guide explains how to integrate the Report Engine backend into your own Spring Boot
application. The bundled `report-engine-example` module is a complete, runnable reference of
everything described here — when in doubt, read its source.

> **Java 17+ and Spring Boot 3.x are required.** The framework uses sealed interfaces and records.

## 1. Module overview

| Module | Coordinate (`com.codingapi.report`) | What it gives you |
|---|---|---|
| `report-engine-framework` | declarative data model + in-memory render engine + storage SPIs (zero Spring deps) |
| `report-engine-excel` | JSON ↔ `.xlsx` conversion + font management (Apache POI) |
| `report-engine-starter` | Spring Boot auto-configuration + **all REST APIs** + DTO conversion |

Dependency direction: `starter → framework → excel`. You normally depend on **starter** (which
pulls in framework + excel transitively) and optionally on **framework** directly if you build
domain objects yourself.

## 2. Add dependencies

```xml
<dependency>
    <groupId>com.codingapi.report</groupId>
    <artifactId>report-engine-starter</artifactId>
    <version>0.0.1</version>
</dependency>
<dependency>
    <groupId>com.codingapi.report</groupId>
    <artifactId>report-engine-framework</artifactId>
    <version>0.0.1</version>
</dependency>
```

You also need `spring-boot-starter-web`. If you connect to databases, add the relevant JDBC
drivers (the example bundles `h2`, `mysql-connector-j`, `postgresql`):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<!-- JDBC drivers as needed -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
```

> The starter registers two auto-configurations via
> `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
> `ReportEngineAutoConfiguration` and `DataModelMgmtAutoConfiguration`. A plain
> `@SpringBootApplication` picks them up automatically — no `@Import` needed.

## 3. What the starter auto-configures

`ReportEngineAutoConfiguration` registers (each with `@ConditionalOnMissingBean`, so you can
override any of them) the full REST surface and the services behind it:

- **Infrastructure**: `FontRegistry`, `FontController`, `ExcelController`
  (`/api/fonts/list`, `POST /api/excel/generate`, `POST /api/excel/import`)
- **Report engine**: `ReportRenderController` (`POST /api/report/render`),
  `ReportConfigController` (`/api/report/configs[...]`), `DatasetController`,
  `ExpressionController` (`GET /api/expression/functions`)
- **Data management**: `DataModelMgmtController` (`/api/datamodels[...]`),
  `DataSourceController` (`/api/datasources[...]`), `DataSourceTypeService`, `DriverLoader`

The CSV / Excel / DB `DataExtractor` beans are registered too, so reading from those source
types works out of the box.

**You do not write any controllers.** You only provide storage implementations (next section).

## 4. Provide storage implementations (required)

The framework defines four storage SPIs in `com.codingapi.report.repository`. The starter does
**not** ship default implementations — you must supply them (this is the one mandatory
integration step). All four work with **domain objects** and the Spring-free paging types
`PageQuery` / `PageResult<T>`:

| Interface | Stores | Key methods |
|---|---|---|
| `ReportRepository` | `core.Report` | `save / find / page(PageQuery):PageResult<Report> / delete` |
| `DataModelRepository` | `data.datamodel.DataModel` | same shape |
| `DataSourceRepository` | `data.datasource.DataSource` | same shape |
| `DataSourceTypeRepository` | DB driver type configs | same shape |

Register them as beans. The example uses in-memory maps (lost on restart) — replace with
JPA / MyBatis / files for production:

```java
@Configuration
public class RepositoryConfig {

    @Bean
    public ReportRepository reportRepository() {
        return new InMemoryReportRepository();   // your persistence here
    }

    @Bean
    public DataModelRepository dataModelRepository() {
        return new InMemoryDataModelRepository();
    }

    @Bean
    public DataSourceRepository dataSourceRepository() {
        return new InMemoryDataSourceRepository();
    }

    @Bean
    public DataSourceTypeRepository dataSourceTypeRepository() {
        return new InMemoryDataSourceTypeRepository();
    }
}
```

A minimal `ReportRepository` implementation sketch:

```java
public class InMemoryReportRepository implements ReportRepository {
    private final Map<String, Report> store = new ConcurrentHashMap<>();

    @Override
    public String save(Report report) {
        if (report.getId() == null) report.setId(UUID.randomUUID().toString());
        long now = System.currentTimeMillis();
        if (report.getCreateTime() == 0) report.setCreateTime(now);
        report.setUpdateTime(now);
        store.put(report.getId(), report);
        return report.getId();
    }

    @Override public Report find(String id) { return store.get(id); }
    @Override public void delete(String id) { store.remove(id); }

    @Override
    public PageResult<Report> page(PageQuery query) {
        List<Report> all = new ArrayList<>(store.values());
        // apply query.current()/query.pageSize() paging, then:
        return new PageResult<>(all, all.size());
    }
}
```

> Domain entities `core.Report` and `data.datamodel.DataModel` carry their own
> `toDTO()` / `fromDTO()` — your repository never touches DTOs, it stores the domain object
> directly. Credentials (DB passwords) live in `DataSource.config` as plaintext in memory and
> are masked only at the DTO boundary; if you persist to disk, encrypt them in your repository.

## 5. Configuration properties

All under the `codingapi.report` prefix (`application.properties`):

```properties
server.port=8090

# custom font directory (built-in fonts are always loaded)
codingapi.report.font.dir=.fonts/

# upload/storage directories (defaults shown)
codingapi.report.driver.dir=./data/drivers
codingapi.report.excel.dir=./data/excel
codingapi.report.csv.dir=./data/csv

# JDBC driver jars can be large — raise multipart limits so uploads aren't rejected
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB
```

## 6. Bootstrap your application

Nothing special — a standard Spring Boot entry point:

```java
@SpringBootApplication
public class ServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
```

On startup the engine APIs are live. Verify:

```bash
curl http://localhost:8090/api/expression/functions   # returns the formula catalog
```

## 7. Seeding initial data (optional)

The example seeds demo data models and reports on `ApplicationReadyEvent`
(`DataModelSeeder`, `ReportTemplateSeeder`). To preload your own:

```java
@Component
public class MySeeder {
    private final DataModelRepository dataModelRepo;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        DataModel model = DataModel.builder()
            .id("default").name("Demo Model")
            // .datasets(...).relationships(...).transforms(...)
            .build();
        dataModelRepo.save(model);
    }
}
```

## 8. Extension points

The framework exposes four SPIs, all following the same `supports()` + registry pattern.
Implement the interface and register it; unregistered operators/functions throw explicitly
(never silently pass).

| Extend | Interface | Example |
|---|---|---|
| New data source type | `DataExtractor` | read from a REST API |
| New comparison operator | `ConditionPredicate` | `REGEX_MATCH` |
| New aggregation | `Aggregator` | `MEDIAN` |
| New expression function | `ValueFunction` | a custom `mask()` |

Built-in functions: `format`, `date`, `round`, `concat`, `if`, and `map` (the data-transform
dictionary lookup, configured per data model). To add one, implement `ValueFunction` and
register it in the `Functions` registry; the new function automatically appears in
`GET /api/expression/functions`.

## 9. SQL datasets & data transforms (cheat sheet)

- **SQL dataset**: a `TableDataset` whose `sourceTable` is a full `SELECT` statement (instead
  of a table name). `DbDataExtractor` executes it directly. Created in the data-source UI; do
  any dictionary `JOIN` inside the SQL.
- **Transform items**: dictionaries (`code → label`, tree-capable) configured under a
  `DataModel` and referenced in a report cell as `map(field, "transformId")`. Numeric codes are
  normalized at render time (a DB `int` `0` arriving as `Double 0.0` still matches the `"0"`
  dictionary entry).

## Reference

`report-engine-example` is the canonical working integration. Run it with:

```bash
./mvnw spring-boot:run -pl report-engine-example   # starts on :8090
```
