# JPA N+1 Lab

> JPA N+1 문제를 직접 재현하고, 해결 전략들을 **실제 실행된 쿼리 수**로 비교하는 실습 프로젝트
>
> "fetch join이면 다 해결된다"가 왜 틀렸는지, 컬렉션이 여러 개거나 페이징이 걸리는 상황까지 테스트로 확인합니다.

<br>

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3, Spring Data JPA (Hibernate 6) |
| DB | PostgreSQL 16 (Docker Compose) |
| Test | JUnit 5, AssertJ, Hibernate Statistics |

<br>

## 도메인

```
Author 1 ──< N Book
Author 1 ──< N Award
```

- 모든 `@ManyToOne`은 `LAZY`입니다.
- 테스트 데이터: 작가 10명 × 책 5권 (수상 이력이 필요한 테스트는 작가당 3건)

<br>

## 쿼리 수를 어떻게 셌나

`QueryCounter`가 Hibernate `Statistics`를 사용합니다.

| 지표 | 의미 | 쓰는 이유 |
|---|---|---|
| `prepareStatementCount` | 실제로 DB에 나간 JDBC 문장 수 | 지연 로딩으로 발생한 SELECT까지 모두 셈. `queryExecutionCount`는 명시적 JPQL만 셈 |
| `entityLoadCount` | 메모리에 로딩된 엔티티 수 | 쿼리 수는 적어도 **데이터를 너무 많이 가져오는 문제**를 잡기 위해 |

<br>

## 실험과 검증 내용

### 1. 기본 전략 비교: `NPlusOneStrategiesTest`

| 전략 | 구현 | 기대 쿼리 수 |
|---|---|---|
| 기본 지연 로딩 | `findAll()` 후 `getBooks()` 순회 | **1 + N (11)** |
| fetch join | `join fetch a.books` | 1 |
| `@EntityGraph` | `attributePaths = "books"` | 1 |
| DTO 프로젝션 | `select new ...AuthorBookCountDto(...) group by` | 1 (엔티티 로딩 없음) |

### 2. 배치 크기: `BatchFetchSizeTest`

- `hibernate.default_batch_fetch_size=5`
- 작가 10명의 책을 `IN` 절로 묶어 조회합니다. **1 + ⌈10/5⌉ = 3번**
- 엔티티 코드를 바꾸지 않고 설정만으로 N+1을 줄일 수 있습니다.

### 3. 컬렉션이 두 개일 때: `MultipleCollectionFetchTest`

- `books`와 `awards`를 동시에 fetch join하면 **`MultipleBagFetchException`** 이 발생합니다.
  - 카티션 곱 때문에 Hibernate가 두 `List`(bag)를 동시에 채우지 못하기 때문입니다.
- 배치 크기를 쓰면 예외 없이 **1 + 2 + 2 = 5번**으로 해결됩니다.

### 4. 페이징과 함께 쓸 때: `PagingStrategiesTest`

| 전략 | 반환 페이지 크기 | 실제 로딩된 작가 |
|---|---|---|
| 배치 크기 + `findAll(Pageable)` | 5 | **5** (요청한 페이지만) |
| 컬렉션 fetch join + `Pageable` | 5 | **10** (테이블 전체) |

- 컬렉션 fetch join에 페이징을 걸면 SQL에 `LIMIT`가 붙지 않습니다.
- Hibernate가 **전체를 메모리로 가져온 뒤 자바에서 자릅니다** (`HHH000104` 경고).
- 반환 결과는 정상처럼 보여서 놓치기 쉬운 함정입니다.

<br>

## 상황별 선택 기준

| 상황 | 추천 |
|---|---|
| 단건 또는 목록에서 `@ManyToOne`, 컬렉션 하나 | fetch join 또는 `@EntityGraph` |
| 컬렉션을 페이징해야 함 | `default_batch_fetch_size` (보통 100~1000) |
| 컬렉션이 두 개 이상 | `default_batch_fetch_size` |
| 화면에 필요한 값만 집계, 조회 | DTO 프로젝션 |

<br>

## 실행 방법

```bash
docker compose up -d   # PostgreSQL (localhost:5432)
./gradlew test         # 테스트별 쿼리 수가 콘솔에 출력됨
```

각 테스트는 `@Transactional`로 감싸 끝나면 롤백되므로, 여러 번 실행해도 데이터가 쌓이지 않습니다.
