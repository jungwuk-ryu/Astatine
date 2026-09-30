# Netty 4.2.18 / io_uring 검증 결과

검증일: 2026-09-30 KST. Netty 4.2.18을 기존 epoll 구성으로 Earth에 배포했다. io_uring 활성화는 보류한다. 성능 이득이 반복 실행에서 일관되지 않았고, Grim을 포함한 40명 합성 클라이언트 장시간 검사도 완주하지 못했다. Netty 업그레이드의 배포 대상은 기존 epoll 구성이다.

## 검증한 아티팩트

- 서버 엔진 변경: `6ee8f2b96ccc0e34e029e231031c2bdf5cd76559` (`fix: update Netty native transports to 4.2.18.Final`). BOM과 세 io_uring native classifier를 함께 변경했다. 번들 Netty 모듈 20개 모두 `4.2.18.Final`이고 혼합 버전은 없었다.
- Paperclip SHA-256: `1ef48f36fa9ce64d5187239258905ef41938c27b2642984624e7a30bb5eba906`.
- 이전 Earth JAR SHA-256: `cab18ab7d2c8dbfba3a2350cfaa4fe408f0e307b2cbd7c6f56586ba30aa6c547`.
- 기존 로컬 ownership 변경도 빌드에 포함되어 있다. 해당 소스 diff SHA-256은 `9626ae9af28d665a7d73ba2a8b6d3ed8d5d021151cebdac1091d32c2e8b44057`이며 이전 운영 아티팩트와 동일한 변경이다. 이번 작업의 커밋에는 포함하지 않았다. 기존 작업 파일 9개의 해시도 보존했다.
- API/서버 전체 테스트: 총 9,613개, 성공 9,589개, skipped 24개, 실패/오류 0개. Paperclip 생성 및 최종 ZIP CRC 검사를 통과했다.

[공식 4.2.18 릴리스](https://netty.io/news/2026/09/09/4-2-18-Final.html)의 [#17238](https://github.com/netty/netty/pull/17238)은 특정 io_uring 쓰기 오류 및 `shutdownOutput()`과 진행 중 쓰기가 겹칠 때 버퍼 수명을 보호한다. 사용자가 기억하는 과거 접속 오류의 원인이 이 수정이었다는 증거는 없다.

## 실제 vanilla 클라이언트

공식 Minecraft 1.21.11 client JAR의 SHA-1 `ba2df812c2d12e0219c489c4cd9a5e1f0760f5bd`를 배포 manifest와 대조했다. Xvfb에서 실행한 수정하지 않은 클라이언트로 접속, 키보드 이동, 서버 좌표 변경, Nether 왕복, 종료 후 플레이어 제거를 확인했다. 서버도 정상 종료했다.

QA에는 다음 운영 플러그인 JAR 세 개를 복사했으며 운영 설정은 복사하지 않았다.

| 플러그인 | 실제 버전 | SHA-256 |
| --- | --- | --- |
| PacketEvents | 2.12.1 | `a58ec1e9a3735c633af39506009eed2f22d888b2ca500effb3cba2803e79323f` |
| ProtocolLib | 5.5.0-SNAPSHOT-b723ff3 | `d0659b9f9306241d25dab28540b639bdf0d2f5d0c8de111881bb23b16ac535ce` |
| GrimAC | 2.3.74-8fce090 | `b24967494590bbe4cb01ee41e9c19bd314709b5a7423eb9889b215a7667ce9f2` |

io_uring에서 offline 접속 3회와 RSA/AES 암호화 접속 4회, epoll에서 암호화 접속 2회를 통과했다. io_uring 암호화 검사 중 한 회는 차원 이동 후 120초 동안 실제 서버 player list로 연결을 확인했다.

암호화 검사는 loopback session API fixture로 클라이언트와 서버의 session hash 일치를 검증했다. 실제 Mojang 계정 인증 및 Earth whitelist 접속을 검증한 것은 아니다. 운영의 online-mode, whitelist, 인증 서버는 변경하지 않았다. fixture의 profile/key 서비스 404와 기존 Grim SLF4J/deprecated-listener 경고는 원본 로그에 남겼다.

일부 io_uring 클라이언트 종료에서는 기존 owner handoff watchdog의 3초 경고가 발생했지만 이후 플레이어가 제거됐다. 경고가 전혀 없었다고 판정하지 않는다.

장시간 native 검사는 **두 transport 모두 1명씩 1,800초 연결 유지**를 통과했다. 운영의 세 네트워크 플러그인 JAR을 포함하고 각자 분리된 loopback server/client/display를 사용했다. 이동과 차원 왕복 후 5초마다 실제 서버 player list를 확인했으며, client 종료 후 플레이어 제거, controller/server exit 0/0을 확인했다. 실제 접속 오류는 이 새 Netty 아티팩트의 vanilla 검사에서 재현되지 않았다. 이 결과는 40명 실제 vanilla 또는 운영 64개 플러그인의 플레이 부하 검사가 아니다.

native 검사 후반 약 17분의 `/proc` 관찰(종료 구간 포함): native-long-epoll: RSS 1462–1631 MiB, FD 176–177; native-long-io-uring: RSS 1353–1460 MiB, FD 153–174. 마지막 구간까지 급격한 FD 누적은 관찰되지 않았다. 시작 구간 전체를 기록한 것은 아니며 RSS는 heap/direct-memory를 분리하지 않으므로 장기 누수 부재를 증명하지 않는다.

## Minecraft 부하 A/B

같은 새 JAR에서 전송 방식만 변경했다. Linux 6.17 aarch64, Earth와 동일한 GraalVM Java 25, 압축 threshold 512, Netty worker 설정 4, region worker 7, heap 1–3GiB, view/simulation distance 2로 실행했다. 각 실행은 이동하는 loopback 합성 클라이언트 40명과 정상 AI의 소 240마리를 같은 연결 지역에서 처리했다. 소는 persistent/invulnerable이며 cramming 24, entity collision limit 2다. 이 성능 A/B에는 플러그인과 로그인 암호화가 없다.

순서는 epoll → io_uring / io_uring → epoll / epoll → io_uring의 3쌍이다. 6개 유효 실행 모두 190초 샘플, 39명씩 상호 가시성, 계속 증가하는 keepalive, 소 240마리의 전후 확인, 오류 0건, 서버 정상 종료를 통과했다. 플레이어만 있는 45초와 소가 있는 60초 JFR을 따로 기록했다. packet diagnostic JFR 이벤트는 비활성화했다.

| 소 240마리 구간 | epoll | io_uring |
| --- | ---: | ---: |
| Netty CPU 평균, core | 0.781 | 0.730 |
| Netty CPU ms / 클라이언트 수신 MiB | 241.90 | 225.65 |
| 클라이언트 압축 TCP 수신 MiB/s | 3.232 | 3.232 |
| busiest region p95, 실행별 값의 중앙값 | 28.16 ms | 26.43 ms |
| busiest region p99, 실행별 값의 중앙값 | 34.79 ms | 33.87 ms |

Netty CPU 평균은 약 6.6% 낮았지만 쌍별 변화는 **−23.3%, −10.1%, +16.4%**였다. p95 변화는 **−1.6%, +15.2%, −32.5%**, p99 변화는 **−15.1%, +40.4%, −28.4%**였다. Netty system CPU는 세 실행에서 모두 감소했지만 user CPU 변화가 커서 전체 비용 및 tail latency가 일관되게 개선되지 않았다. 플레이어만 있는 구간의 p95/p99 중앙값은 epoll 4.50/6.99 ms, io_uring 6.79/10.25 ms였다.

공유 호스트의 다른 QA 및 운영 프로세스가 실행 중이고 부하와 JIT 효과가 변동했다. 세 쌍만으로 통계적 유의성, 운영 TPS, 수용 인원 증가를 주장하지 않는다. Thread CPU는 양 경계에 존재하는 스레드의 user/system 누적 차이이며, 종료/신규 스레드와 IRQ CPU를 포함하지 않는다. 수신 바이트와 CPU 측정 경계에도 polling 차이가 있다. RSS 증가만으로 direct-memory 누수를 판정하거나 배제할 수 없다.

한 io_uring 실행은 외부 실행 프로세스 종료와 함께 중단되어 `perf-3-io_uring-interrupted`로 보존하고 비교에서 제외했다. 같은 조건의 재실행만 6개 유효 실행에 포함했다. 일부 외부 runner exit 143이 서버 정상 종료 및 DONE 이후 발생한 기록도 있다. 원인이 확인되지 않은 외부 종료를 Netty crash로 분류하지 않는다.

## 플러그인 및 장시간 검사

수신자별 패킷 변환, 순서/내용, 압축, custom-payload echo, 차원 전환의 35초 검사는 세 플러그인을 포함해 양쪽 transport에서 통과했다.

| 검사 | epoll | io_uring |
| --- | ---: | ---: |
| 변환 패킷 | 154,816 | 157,312 |
| echo 응답 | 2,415 | 2,454 |
| echo RTT p50/p95/p99 | 50.04/54.10/60.30 ms | 50.04/54.65/94.09 ms |
| client/server exit | 0/0 | 0/0 |

위 RTT는 약 50ms tick 간격의 smoke 결과이며 transport의 성능 이득을 입증하지 않는다. 별도 io_uring smoke 재실행도 변환 159,168회, echo 2,483회, 오류 없이 정상 종료했다.

Grim 포함 40명 장시간 합성 검사는 **실패했으며 배포 성공 근거에서 제외했다**.

1. 최초 io_uring fixture는 Ping/Pong 및 tick-end 응답이 불완전해 Grim timeout을 냈다. 이 기록은 `soak-io-uring-incomplete-client`에 보존했다. 응답을 보완했다.
2. 보완한 io_uring 검사도 약 476초 후 한 플레이어의 keepalive timeout 및 가시성 손실로 실패했다. 서버는 정상 종료했으며 연결 종료를 성공으로 처리하지 않았다.
3. epoll 대조 검사에서도 약 130초 샘플 후 서버가 플레이어들을 timeout으로 제거했고 마지막 소 selector 확인이 실패했다. 클라이언트의 cached visibility만으로 정상 연결을 주장할 수 없었다.

양쪽 실패만으로 io_uring 고유 결함을 확정할 수 없다. 합성 클라이언트는 vanilla physics와 전체 anti-cheat 동작을 구현하지 않는다. 반대로 합성 fixture 문제라고 단정하여 실패를 무시하지도 않는다. 이 조합의 장시간 검증은 미해결이며 io_uring 활성화 기준을 충족하지 않는다. 운영 Grim을 끄거나 keepalive timeout을 늘려 성공 판정을 만들지 않았다.

PacketEvents·ProtocolLib 두 플러그인으로 분리한 epoll 검사는 40명·소 240마리, 600초의 전체 상호 가시성과 계속 증가하는 keepalive를 통과했다. 5분/8분 시점에 실제 서버 인원과 소 개수를 확인하고 각각 60초 JFR을 기록했다. 오류 0건, controller/server exit 0/0이다. **이 합성 장시간 검사에는 Grim이 없다.**

이 실행의 mixed/5분/8분 p95는 48.74/49.02/42.08 ms, p99는 60.85/57.98/48.73 ms였다. JFR의 전체 호스트 CPU load는 96.6–98.2%였다. 기능 검사는 통과했지만 모든 tick이 50ms 이내였거나 운영 TPS 용량을 확인했다는 의미는 아니다.

이후 같은 네 프로필을 25회 재사용해 총 100회 접속했다. 매 회 실제 서버 인원 4명을 확인하고 절반은 socket destroy, 절반은 정상 close를 사용했으며, 5회는 두 reader를 10초 중지했다. 모든 회차에서 서버 인원이 0명으로 돌아왔고 최대 확인 시간은 4.08초였다. reader 중지는 로컬 애플리케이션 수준 검사이며 실제 kernel backpressure 또는 WAN 상황을 측정한 것은 아니다.

첫 epoll 30분 native 검사의 외부 runner가 약 20분 후 exit 143으로 종료했다. 이후에도 독립된 서버 JVM과 vanilla JVM은 살아 있었고 실제 player list에 NativeUringQA 1명이 있었다. 이 기록은 `native-long-epoll-interrupted`로 분리하여 완주 결과에서 제외했고, 해당 client/server/display만 정리한 뒤 detached screen에서 다시 검사했다.

## Earth 배포

Netty 4.2.18을 **epoll 구성으로 배포 완료**했다. `optimizations.prefer-io-uring-transport=false`, 실제 io_uring ring 0개다. 새 PID는 `1708280`, 엔진 버전은 `6ee8f2b`이며 실제 프로세스가 연 core Netty JAR 20개 모두 `4.2.18.Final`이다. 운영 인증/whitelist 설정과 최신 플러그인 JAR 해시는 보존했다.

최초 prepare 단계에서는 접속 인원 1명 때문에 Trade가 `Reload preparation requires zero online players`로 안전 토큰 발행을 거부했다. 이때 엔진은 교체하지 않았다. 요청된 재시작에 필요한 일시 접속 종료 후 0명을 확인하고 prepare를 다시 수행했다. Wars, WorldGuard, Border, Trade의 SAFE_TO_RELOAD를 모두 받았으며, Trade는 writer TERMINATED / queue 0/0 / one-use token PRESENT를 확인했다. 이후 `save-all flush`의 `Saved the game` 확인, 아티팩트 교체, 기존 JVM 정상 종료, supervisor의 새 JVM 기동을 수행했다.

- 서버 정상 재시작: **1회**. 이전 PID `3148370`은 18:37:00 KST에 종료됐고 새 서버는 18:38:26에 ready가 됐다.
- 부팅 중 Multiverse의 비동기 Earth 월드 로드가 Border/EarthTravel의 onEnable보다 늦었다. 두 플러그인이 `Earth world is not loaded`로 비활성화됐고, Earth 월드 로드는 18:38:31에 완료됐다. 월드 로드 완료 후 기존 JAR 그대로 두 플러그인을 각각 PlugMan reload하여 Border ACTIVE와 EarthTravel의 orbital gate 검증을 복구했다. 추가 서버 재시작은 없었다. 이 부팅 순서 문제의 영구적인 플러그인 수정은 이번 Netty 변경에 포함하지 않았으므로 다음 콜드 부팅에서도 재확인이 필요하다.
- 18:43:30 readback: 플러그인 64개 enabled, disabled 0개. Wars RUNNING, Border ACTIVE / inFlight 0 / packetFailures 0, Trade READY / writer ACCEPTING / queue 0/0 / token ABSENT / natural 21099 / loaded 0 / pending 0 / failures 0 / unsafe false. WorldGuard active/pending 0.
- TCP status ping: protocol 774, max players 45, 정상 응답. 확인 시 온라인 0명이며 이 건강 상태를 운영 플레이 또는 최대 용량 검증으로 표현하지 않는다.
- 18:50 이후 재확인에서도 동일 PID와 플러그인 64개 enabled / disabled 0을 유지했고, 복구 이후 새 ERROR/Exception 또는 reload protocol violation은 없었다. 기존 작업 파일 9개 해시도 유지됐다.
- 이전 JAR·설정과 rollback helper는 `.runtime/releases/io-uring-20260930-6ee8f2b/`에 보존한다. 초기 준비 실패, 재개, 네 drain token, 저장, 부팅 및 복구 로그도 원본에 포함했다.

## 원본과 재현

원본은 Git에서 제외된 `run/io-uring-validation-20260930/`에 보존한다. `artifact.json`, `full-tests.json`, `build.log`, `package.log`, `performance.json`, 각 실행의 `manifest.json`, `clients.json`, JFR 및 summary, native screenshot/client/server 로그, 실패한 soak 및 중단 기록을 포함한다. 재현 명령과 측정 범위는 [README](README.md)에 있다. 최종 배포 manifest 및 readback도 별도로 보존한다.
