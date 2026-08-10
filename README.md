# salesmgmt MySQL 저장 버전

## 구현된 기능

1. `input_data.xlsx` 업로드 및 검사
2. 가로형 판매 입력을 세로형 판매 데이터로 변환
3. MySQL에 거래처, 주문, 주문품목 저장
4. 같은 `주문번호 + 품목` 재업로드 시 중복 저장 방지
5. `/sales`에서 최근 저장 데이터 확인
6. `명희네해장` → 명세서명 `명희네` 연결
7. `산동빅` 데이터 저장 허용, 명세서 템플릿 없음 경고

## 1. MySQL 데이터베이스 만들기

MySQL Workbench에서 `database/create_database.sql`을 실행하거나 아래 SQL을 실행합니다.

```sql
CREATE DATABASE IF NOT EXISTS bean_sprout
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

## 2. 비밀번호 설정

`src/main/resources/application.yml`에서 다음 값을 본인의 MySQL 비밀번호로 변경합니다.

```yaml
password: ${DB_PASSWORD:YOUR_MYSQL_PASSWORD}
```

예를 들어 비밀번호가 `1234`라면:

```yaml
password: ${DB_PASSWORD:1234}
```

실제 배포 단계에서는 비밀번호를 GitHub에 올리지 말고 환경변수 `DB_PASSWORD`로 설정해야 합니다.

## 3. 실행

`SalesMgmtApplication.java`를 실행한 뒤 접속합니다.

- 업로드: http://localhost:8080
- 저장 데이터: http://localhost:8080/sales

처음 실행하면 Hibernate가 아래 테이블을 자동 생성합니다.

- `vendors`
- `sales_orders`
- `sales_items`

## 저장 규칙

- 엑셀 오류가 한 건이라도 있으면 저장하지 않음
- 같은 주문번호가 기존과 다른 날짜 또는 거래처로 들어오면 전체 저장 취소
- 같은 주문번호와 품목은 중복 저장하지 않음
- 기존 주문에 없는 새 품목만 추가 저장 가능
