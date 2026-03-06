# 🌱 Spring Advanced 과제

## 📌 프로젝트 소개

Spring Boot 기반의 일정 관리 애플리케이션입니다.
JWT 인증, JPA, AOP 등 Spring의 핵심 기술을 활용하여 구현했습니다.

---

## ✅ 구현 기능

### Lv 0. 프로젝트 세팅 - 에러 분석

**해결 방법**

- application.yml이 누락되어 @Value("${jwt.secret.key}")로 주입받는 시크릿 키 값을 찾지 못해 애플리케이션 실행에 실패했습니다.
  .gitignore에 *.properties가 등록되어 있어 설정 파일이 git에서 제외된 상태였습니다.
  application.yml을 직접 생성하여 DB 연결 정보 및 JWT 시크릿 키를 설정함으로써 해결했습니다.

---

### Lv 1. ArgumentResolver

**해결 방법**

- `WebMvcConfig`에서 `addArgumentResolvers()`로 resolver를 등록하여 컨트롤러에서 `@Auth AuthUser authUser`로 사용자 정보를 주입받을 수 있도록 했습니다.

---

### Lv 2. 코드 개선

**1. Early Return**

- `AuthService.signup()`에서 이메일 중복 검사(`userRepository.existsByEmail()`)를 `passwordEncoder.encode()` 호출 이전으로 이동했습니다.
- 중복 이메일인 경우 즉시 예외를 던져 불필요한 BCrypt 암호화 연산을 방지했습니다.

**2. 불필요한 if-else 제거**

- `WeatherClient.getTodayWeather()`의 중첩된 `if-else` 구조를 Early Return 패턴으로 리팩토링했습니다.
- 상태 코드가 200이 아닌 경우 즉시 `throw`하고 `else` 블록 없이 이후 로직이 자연스럽게 이어지도록 개선하여 코드 가독성을 높였습니다.

**3. Validation**

- `UserService.changePassword()`의 비밀번호 유효성 검사 로직을 `UserChangePasswordRequest` DTO로 이동했습니다.
- `spring-boot-starter-validation`의 `@Pattern` 어노테이션에 정규식 `^(?=.*[0-9])(?=.*[A-Z]).{8,}$`을 적용하여 8자 이상, 숫자, 대문자 포함 조건을 DTO 레벨에서 검증하도록 개선했습니다.

---

### Lv 3. N+1 문제

**해결 방법**

- `TodoRepository`의 `findAllByOrderByModifiedAtDesc()`에 `@EntityGraph(attributePaths = {"user"})`를 적용했습니다.
- 기존 JPQL `fetch join` 방식과 동일하게 `Todo` 조회 시 연관된 `User`를 한 번의 쿼리로 함께 가져와 N+1 문제를 해결했습니다.
- `@EntityGraph`는 JPQL을 직접 작성하지 않아도 되므로 Spring Data JPA의 메서드 네이밍 전략을 그대로 활용할 수 있다는 장점이 있습니다.

---

### Lv 4. 테스트 코드

**1. PasswordEncoderTest**

- `@InjectMocks`로 `PasswordEncoder`를 주입받아 실제 BCrypt 암호화 및 검증이 동작하는지 확인했습니다.
- `matches()`과정에서 비교 파라미터의 순서가 올바르지않아 실패했던 문제를 수정했습니다.

**2. ManagerServiceTest - Case 1**

- 실제로 던지는 예외가 `NullPointerException`이 아닌 `NotFoundException`임을 확인하고 테스트 코드와 메서드명을 함께 수정했습니다.
- `todoService.getTodoById()`가 `NotFoundException`을 던지도록 `given()`을 설정하고, `assertThatThrownBy()`로 검증했습니다.

**3. CommentServiceTest - Case 2**

- `todoService.getTodoById()`가 `NotFoundException`을 던질 때 `commentService.saveComment()`도 동일한 예외를 전파하는지 검증하도록 테스트를 수정했습니다.

**4. ManagerServiceTest - Case 3**

- `todo.getUser()`가 `null`인 경우 `InvalidRequestException`이 발생하도록 서비스 로직 `validateTodoOwner()`에 null 체크 조건을 추가했습니다.
- `new Todo()`로 생성한 user가 없는 Todo를 `given()`으로 설정하여 테스트가 성공하도록 수정했습니다.

---

### Lv 5. API 로깅 (AOP)

**해결 방법**

- `@AdminLog` 커스텀 어노테이션을 생성했습니다.
- `ApiLoggingAspect`에서 `@Around` 어드바이스를 활용하여 어드민 API 호출 시 아래 정보를 로깅합니다.
    - 요청한 사용자 ID
    - API 요청 시각
    - API 요청 URL
    - 요청 본문 (RequestBody)
    - 응답 본문 (ResponseBody)
- 적용 대상
    - `CommentAdminController.deleteComment()`
    - `UserAdminController.changeUserRole()`

---

### Lv 6. Refresh Token 도입

**1. 문제 인식 및 정의**

기존 인증 방식은 Access Token 하나만 발급하며 유효기간이 60분으로 고정되어 있었습니다. 이는 두 가지 문제를 야기합니다.

- **보안 취약점**: Access Token이 탈취되면 만료 전까지 60분간 악용 가능
- **사용성 저하**: 보안을 위해 유효기간을 줄이면 사용자가 자주 재로그인해야 하는 불편함 발생

**2. 해결 방안**

**의사결정 과정**

| 방식 | 장점 | 단점 |
|---|---|---|
| Access Token 유효기간 단축만 적용 | 구현 단순 | 사용자 불편, 근본적 해결 아님 |
| Refresh Token 도입 | 보안과 사용성 동시 해결 | 구현 복잡도 증가 |

Refresh Token 방식을 채택하여 Access Token의 유효기간을 30분으로 단축하고, 14일짜리 Refresh Token을 별도 DB 테이블에 저장하는 방식으로 구현했습니다.

**해결 과정**

`RefreshToken` 엔티티를 `domain.auth.entity` 패키지에 생성하고 `User`와 `@OneToOne` 관계로 설계했습니다. 한 유저당 하나의 Refresh Token만 운영하며, 재발급 시 `rotate()` 메서드로 토큰을 갱신합니다 (Token Rotation 패턴 적용).

추가된 API는 다음과 같습니다.

| API | Method | 설명 |
|---|---|---|
| `/auth/refresh` | POST | Refresh Token으로 새 토큰 쌍 재발급 |
| `/auth/logout` | POST | Refresh Token DB에서 삭제 |

기존 `/auth/signup`, `/auth/signin` API의 응답도 `TokenPairResponse`로 변경하여 Access Token과 Refresh Token을 함께 반환합니다.

**3. 해결 완료**

**회고**

Refresh Token을 DB에 저장함으로써 서버가 재시작되어도 토큰 상태가 유지되고, 로그아웃 시 즉시 무효화가 가능해졌습니다. Token Rotation 패턴을 적용하여 Refresh Token 탈취 시 재사용 공격도 방어할 수 있습니다.

**전후 비교**

| 항목 | 변경 전 | 변경 후 |
|---|---|---|
| Access Token 유효기간 | 60분 | 30분 |
| Refresh Token | 없음 | 14일 (DB 저장) |
| 로그아웃 | 미지원 | Refresh Token 삭제로 완전 로그아웃 |
| 응답 DTO | `bearerToken` 단일 필드 | `accessToken` + `refreshToken` |

---

### Lv 7. 테스트 커버리지

<!-- 아래에 테스트 커버리지 이미지를 첨부해주세요 -->

---

## 🛠️ 기술 스택

- Java 17
- Spring Boot 3.3.3
- Spring Data JPA
- MySQL
- JWT (jjwt 0.11.5)
- BCrypt
- JUnit 5 / Mockito
