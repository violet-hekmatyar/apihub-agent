# API-HUB 缁熶竴 API 璋冪敤涓庤繑鍥炲绾︼紙淇鐗?v2锛?
## 0. 鏂囨。瀹氫綅

鏈枃妗ｅ畾涔?API-HUB Agent 鍚庣画 **Gateway Invoke銆丮ock Provider銆丼cenario Runner** 涔嬮棿鐨勭粺涓€璋冪敤濂戠害锛屽寘鎷細

- 瀵瑰鏍囪瘑涓庡唴閮ㄤ富閿殑鍖哄垎锛?- 璇锋眰澶淬€乣traceId`銆乣requestId`銆乣scenarioRunId` 鐨勮竟鐣岋紱
- Mock Provider 鍗曚釜 API 鐨勮姹備笌杩斿洖鏍煎紡锛?- API-HUB Gateway Invoke 鐨勭粺涓€璋冪敤鏍煎紡锛?- Scenario Runner 鐨勫紓姝ヨ繍琛屻€佺粨鏋滄煡璇笌鎸佷箙鍖栧绾︼紱
- `gateway_log` 鐨勫瓧娈垫槧灏勫師鍒欍€?
鏈枃妗ｅ彧瀹氫箟鈥滆皟鐢ㄥ绾︹€濄€? 
澶栭儴 API 鐩綍瑙?`06_EXTERNAL_API_CATALOG.md`銆? 
Mock Provider 涓庡満鏅祦閲忔ā鍨嬭 `07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md`銆? 
Agent 璇婃柇娴佺▼瑙?`08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md`銆? 
Smoke 涓?Apifox 楠屾敹瑙?`09_VALIDATION_AND_SMOKE_GUIDE.md`銆?
---

## 1. 鎬讳綋璁捐鍘熷垯

### 1.1 涓夌被鎺ュ彛蹇呴』鍒嗗眰

鏈」鐩悗缁嚦灏戝瓨鍦ㄤ笁绫绘帴鍙ｏ紝涓嶈兘娣峰湪涓€璧疯璁°€?
| 绫诲瀷 | 鎵€灞炴ā鍧?| 浣滅敤 | 鏄惁璁板綍 `gateway_log` |
|---|---|---|---|
| 鍗曚釜澶栭儴涓氬姟 API | `apihub-mock-provider` | 妯℃嫙鏌愪竴涓閮?API 鐨勪竴娆＄湡瀹炶繑鍥?| 涓嶇洿鎺ヨ褰?|
| 缁熶竴缃戝叧璋冪敤鍏ュ彛 | `apihub-server` | 浠ｈ〃 API-HUB 骞冲彴璋冪敤澶栭儴 API | 璁板綍 |
| 鍦烘櫙杩愯鍏ュ彛 | `apihub-mock-provider` | 鎸変笟鍔″満鏅敓鎴愪竴鎵硅皟鐢ㄨ鍒?| 涓嶇洿鎺ヨ褰曪紝鐢?Gateway Invoke 璁板綍 |

姝ｇ‘閾捐矾锛?
```text
Scenario Runner
鈫?API-HUB Gateway Invoke
鈫?Mock Provider 鍗曚釜涓氬姟 API
鈫?API-HUB Gateway Invoke 鍐?gateway_log
鈫?鍚庣画 Stats Aggregator / Alert Evaluator / Agent Tool 鏌ヨ
```

閿欒閾捐矾锛?
```text
Scenario Runner 鐩存帴璋冪敤 Mock Provider 鍗曚釜涓氬姟 API
```

濡傛灉 Scenario Runner 鐩存帴璋冪敤 Mock Provider锛屼細缁曡繃 API-HUB 骞冲彴渚х綉鍏宠褰曪紝鍚庣画 Agent 鏌ヨ涓嶅埌鐪熷疄璋冪敤浜嬪疄銆?
### 1.2 鍗曟璋冪敤涓庨暱鏃堕棿鍦烘櫙杩愯鍒嗗紑

`Gateway Invoke` 鏄崟娆¤皟鐢ㄦ帴鍙ｏ紝閫傚悎绔嬪嵆杩斿洖璋冪敤缁撴灉銆?
`Scenario Runner` 鍙兘杩愯杈冧箙锛屼緥濡傛櫘閫氬伐浣滄棩銆佹姤鍚嶉珮宄般€佸紑瀛﹂珮宄扮瓑锛屽洜姝ら粯璁ゅ簲閲囩敤寮傛鎺ュ彛锛?
```text
POST /mock-provider/scenario-runs
鈫?绔嬪嵆杩斿洖鏄惁鍚姩鎴愬姛鍜?scenarioRunId
鈫?鍚庡彴缁х画鎵ц鍦烘櫙
鈫?鎵ц缁撴灉钀藉簱鎴栨寔涔呭寲
鈫?閫氳繃 GET 鎺ュ彛鏌ヨ杩愯鐘舵€併€佹眹鎬荤粨鏋滃拰鏍蜂緥璋冪敤
```

鍙湁寰堝皬瑙勬ā鐨勬湰鍦板紑鍙戞紨绀猴紝鎵嶅厑璁镐娇鐢?`waitForCompletion=true` 鍚屾绛夊緟銆?
---

## 2. ID 涓庡懡鍚嶈鑼?
### 2.1 鍐呴儴涓婚敭涓庡閮ㄦ爣璇嗗垎寮€

| 绫诲瀷 | 瀛楁鍚嶅缓璁?| 绫诲瀷 | 绀轰緥 | 璇存槑 |
|---|---|---|---|---|
| 鏁版嵁搴撳唴閮ㄤ富閿?| `id` | Long / BIGINT | `1`, `10086` | 鍙敤浜庢暟鎹簱鍏宠仈銆佸垎椤点€佹帓搴?|
| API 涓氬姟鏍囪瘑 | `apiCode` | String | `LECTURE_REGISTER` | 宸插湪鐜版湁鏁版嵁搴撳拰 Tool 涓娇鐢紝缁х画淇濈暀 |
| 璋冪敤鏂规爣璇?| `appCode` | String | `LECTURE_PORTAL` | API 璋冪敤鏂瑰簲鐢ㄧ紪鐮?|
| 鍦烘櫙瀹氫箟 ID | `scenarioId` | Long | `2` | 鍦烘櫙瀹氫箟鐨勬湇鍔＄涓婚敭鎴栧浐瀹氭灇涓?ID |
| 鍦烘櫙瀹氫箟缂栫爜 | `scenarioKey` | String | `lecture-register-peak` | 鏈嶅姟绔繑鍥烇紝鐢ㄤ簬灞曠ず鍜屾帓鏌ワ紝涓嶄綔涓轰富瑕佸惎鍔ㄥ弬鏁?|
| 鍦烘櫙杩愯鎵规 | `scenarioRunId` | String | `sr_20260619_7f3a9c` | 鏈嶅姟绔敓鎴愶紝鐢ㄤ簬鍏宠仈鏈杩愯鐨勬墍鏈夎姹?|
| 鍗曟璇锋眰鏍囪瘑 | `requestId` | String | UUID / ULID | 鍗曟 HTTP 璇锋眰 ID |
| Trace 鏍囪瘑 | `traceId` | String | 32 浣嶅皬鍐欏崄鍏繘鍒?| 璺ㄦā鍧楅摼璺拷韪?ID |
| 瀛﹀彿 | `studentNo` | String | `2023001001` | 鍗充娇鐪嬭捣鏉ユ槸鏁板瓧锛屼篃鎸夊瓧绗︿覆澶勭悊 |
| 璁插骇 ID | `lectureId` | String | `lec_20260619_ai_001` | 涓氬姟瀵硅薄 ID |
| 骞傜瓑閿?| `idempotencyKey` | String | UUID / ULID | 鍐欐搷浣滃箓绛夋帶鍒?|

鏍稿績瑙勫垯锛?
```text
瀛楁鍚嶅彨 id锛屼笖鏄暟鎹簱鍐呴儴涓婚敭锛屼娇鐢?Long銆?瀵瑰鎺ュ彛浼犻€掔殑涓氬姟鏍囪瘑锛岀粺涓€浣跨敤 String銆?鍦烘櫙鍚姩浼樺厛浣跨敤 scenarioId锛岃€屼笉鏄?scenarioName 鎴?scenarioCode銆?```

Gateway Invoke 默认 appCode 必须与 seed 中的 `api_authorization` 保持一致：

| apiCode | defaultAppCode |
|---|---|
| `AUTH_LOGIN` | `COURSE_HELPER` |
| `COURSE_TODAY` | `COURSE_HELPER` |
| `LECTURE_LIST` | `LECTURE_PORTAL` |
| `LECTURE_REGISTER` | `LECTURE_PORTAL` |
| `CAMPUS_NOTICE` | `STUDENT_SERVICE` |
| `VENUE_RESERVE` | `CLUB_ACTIVITY` |
| `LIBRARY_BORROW` | `LIBRARY_MINI` |

### 2.2 ID 鍊兼槸鍚﹀繀椤诲甫鑻辨枃鍓嶇紑

涓嶅己鍒躲€?
浠ュ墠绀轰緥閲屽嚭鐜拌繃锛?
```text
trace_xxx
req_xxx
scenario_run_xxx
```

杩欎簺鍙槸涓轰簡闃呰鏂逛究锛屼笉搴斾綔涓哄己鍒舵牸寮忋€?
鏇存帹鑽愶細

| 瀛楁 | 鎺ㄨ崘鍊兼牸寮?| 鏄惁寤鸿甯﹀墠缂€ |
|---|---|---|
| `traceId` | 32 浣嶅皬鍐欏崄鍏繘鍒讹紝渚嬪 `4bf92f3577b34da6a3ce929d0e0e4736` | 涓嶅缓璁?|
| `requestId` | UUID / ULID锛屼緥濡?`8f7c6b2e-0f6e-4c2f-9c12-2e6c2b1d9a88` | 涓嶅缓璁?|
| `scenarioRunId` | `sr_鏃ユ湡_闅忔満涓瞏锛屼緥濡?`sr_20260619_7f3a9c` | 鍙互 |

璇存槑锛?
```text
瀛楁鍚嶆湰韬凡缁忚〃杈捐涔夛紝鍊奸噷涓嶅繀鍐嶅己鍒跺姞 trace / request / scenario 绛夎嫳鏂囥€?```

`scenarioRunId` 鍙互淇濈暀 `sr_`锛屽洜涓哄畠缁忓父鍑虹幇鍦ㄦ棩蹇楀拰婕旂ず杈撳嚭涓紝鍙鎬ф瘮绾?UUID 鏇村ソ銆?
---

## 3. Trace銆丷equest銆丼cenario Run 鐨勫尯鍒?
### 3.1 涓夎€呬笉鏄噸澶嶅瓧娈?
| 瀛楁 | 绮掑害 | 鍏稿瀷鏁伴噺鍏崇郴 | 浣滅敤 |
|---|---|---|---|
| `scenarioRunId` | 涓€娆″満鏅繍琛屾壒娆?| 1 涓満鏅繍琛?= 澶氫釜 Trace / 澶氫釜 Request | 鍏宠仈鏌愭妯℃嫙浜х敓鐨勬墍鏈夎皟鐢?|
| `traceId` | 涓€鏉¤法妯″潡璋冪敤閾?| 1 涓?Trace = 1 娆℃垨澶氭璺ㄦ湇鍔¤皟鐢?| 鍏宠仈 Scenario Runner 鈫?Gateway Invoke 鈫?Mock Provider |
| `requestId` | 涓€娆?HTTP 璇锋眰 | 1 涓?Trace 涓彲鑳藉寘鍚涓?Request | 绮剧‘瀹氫綅鏌愪竴娆?HTTP 璇锋眰 |

涓句緥锛氫竴娆¤搴ф姤鍚嶉珮宄版ā鎷燂細

```text
scenarioRunId = sr_20260619_7f3a9c

绗?128 娆℃ā鎷熻皟鐢細
  traceId = 4bf92f3577b34da6a3ce929d0e0e4736
  requestId = 8f7c6b2e-0f6e-4c2f-9c12-2e6c2b1d9a88

绗?129 娆℃ā鎷熻皟鐢細
  traceId = 6a1f2c3b4d5e67890123456789abcdef
  requestId = 4c779d8e-7462-4e2b-a22d-f7d7d9cb8d13
```

### 3.2 traceId 鐨勪綔鐢?
`traceId` 鐢ㄤ簬閾捐矾杩借釜銆傚畠鍏虫敞鐨勬槸涓€娆¤皟鐢ㄩ摼缁忚繃浜嗗摢浜涙ā鍧椼€?
鍦ㄦ湰椤圭洰涓紝涓€娆″叿浣撴ā鎷熻皟鐢ㄧ殑 Trace 鍙兘缁忚繃锛?
```text
Scenario Runner
鈫?apihub-server Gateway Invoke
鈫?apihub-mock-provider 鍗曚釜澶栭儴 API
鈫?apihub-server 鍐?gateway_log
```

杩欎簺姝ラ搴斿叡浜悓涓€涓?`traceId`銆?
### 3.3 requestId 鐨勪綔鐢?
`requestId` 鐢ㄤ簬瀹氫綅涓€娆?HTTP 璇锋眰銆傚畠鏇村亸鏃ュ織妫€绱㈠拰闂鎺掓煡銆?
渚嬪 Gateway Invoke 鏀跺埌璇锋眰鏃剁敓鎴愭垨鎺ユ敹涓€涓?`requestId`锛屽啓鍏ユ棩蹇楀悗锛屽彲浠ラ€氳繃杩欎釜 ID 绮剧‘瀹氫綅杩欎竴娆?HTTP 璇锋眰鐨勭綉鍏虫棩蹇椼€?
### 3.4 鏄惁鍙互鍙繚鐣欎竴涓?
涓嶅缓璁€?
鍘熷洜锛?
```text
traceId 瑙ｅ喅璺ㄦā鍧楅摼璺叧鑱斻€?requestId 瑙ｅ喅鍗曟璇锋眰瀹氫綅銆?scenarioRunId 瑙ｅ喅鎵归噺妯℃嫙褰掓。銆?```

涓夎€呰В鍐崇殑闂涓嶅悓锛屽悗缁?Agent 璇婃柇銆佹棩蹇楀洖鏀俱€佸満鏅鐜伴兘浼氱敤鍒般€?
---

## 4. 璇锋眰澶磋鑼?
### 4.1 鎺ㄨ崘璇锋眰澶?
鏂拌璁′紭鍏堜娇鐢ㄦ爣鍑?Trace Context锛?
```http
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
tracestate: vendor=value
X-Request-Id: 8f7c6b2e-0f6e-4c2f-9c12-2e6c2b1d9a88
X-Client-App-Code: COURSE_HELPER
X-Mock-Scenario: RATE_LIMITED
Idempotency-Key: 4b6e5f58-1a5a-4d8c-b21c-8aa51f8e7a13
User-Agent: scenario-runner/1.0
```

| 璇锋眰澶?| 鏄惁蹇呴渶 | 璇存槑 |
|---|---|---|
| `traceparent` | 鎺ㄨ崘 | 鏍囧噯閾捐矾杩借釜澶达紝鍖呭惈 traceId 鍜?parentSpanId |
| `tracestate` | 鍙€?| 鍘傚晢鎵╁睍 Trace 涓婁笅鏂?|
| `X-Request-Id` | 鎺ㄨ崘 | 鍗曟璇锋眰 ID锛屼究浜庢棩蹇楁绱?|
| `X-Client-App-Code` | 鎺ㄨ崘 | 妯℃嫙璋冪敤鏂瑰簲鐢?|
| `X-Mock-Scenario` | 鍙€?| 鏄惧紡瑙﹀彂 Mock Provider 鐗瑰畾杩斿洖 |
| `Idempotency-Key` | 鍐欐搷浣滄帹鑽?| 妯℃嫙閲嶅鎻愪氦銆佸箓绛夋帶鍒?|
| `User-Agent` | 鎺ㄨ崘 | 鏍囪璋冪敤鏉ユ簮 |

鍏煎瑙勫垯锛?
```text
鏂版帴鍙ｄ紭鍏堣瘑鍒?traceparent銆?濡傛灉娌℃湁 traceparent锛屽彲浠ュ吋瀹硅鍙?X-Trace-Id銆?浣嗘枃妗ｇず渚嬩笉鍐嶆帹鑽?X-Trace-Id 浣滀负涓绘柟妗堛€?```

### 4.2 鏄惁杩橀渶瑕?X-Trace-Id

鍙互鍏煎锛屼絾涓嶄富鎺ㄣ€?
鎺ㄨ崘鍋氭硶锛?
| 鎯呭喌 | 澶勭悊鏂瑰紡 |
|---|---|
| 璇锋眰甯?`traceparent` | 浠?`traceparent` 瑙ｆ瀽 traceId |
| 璇锋眰娌℃湁 `traceparent`锛屼絾鏈?`X-Trace-Id` | 鍏煎璇诲彇 `X-Trace-Id` |
| 涓よ€呴兘娌℃湁 | 鏈嶅姟绔敓鎴愭柊鐨?traceId锛屽苟杩斿洖缁欒皟鐢ㄦ柟 |

---

## 5. 缁熶竴杩斿洖鏍煎紡

### 5.1 鎴愬姛鍝嶅簲

缁х画娌跨敤褰撳墠椤圭洰宸叉湁缁熶竴缁撴瀯锛?
```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "requestId": "8f7c6b2e-0f6e-4c2f-9c12-2e6c2b1d9a88",
  "timestamp": "2026-06-19T12:00:00+08:00"
}
```

### 5.2 閿欒鍝嶅簲

涓轰簡閬垮厤宓屽杩囨繁锛岄敊璇搷搴斾笉浣跨敤澶氬眰 `error.code / error.reason / error.details` 缁撴瀯銆? 
缁熶竴閲囩敤鎵佸钩缁撴瀯锛?
```json
{
  "code": 429,
  "message": "too many requests",
  "data": null,
  "errorCode": "RATE_LIMITED",
  "errorDetail": "lecture register rate limit triggered",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "requestId": "8f7c6b2e-0f6e-4c2f-9c12-2e6c2b1d9a88",
  "timestamp": "2026-06-19T12:00:00+08:00"
}
```

瀛楁璇存槑锛?
| 瀛楁 | 璇存槑 |
|---|---|
| `code` | HTTP 鐘舵€佺爜鎴栦笟鍔＄姸鎬佺爜 |
| `message` | 闈㈠悜浜虹殑绠€鐭彁绀?|
| `errorCode` | 闈㈠悜绋嬪簭鍜?Agent 褰掑洜鐨勭ǔ瀹氶敊璇爜 |
| `errorDetail` | 鍙€夛紝琛ュ厖閿欒璇存槑 |
| `traceId` | 閾捐矾杩借釜 ID |
| `requestId` | 鍗曟璇锋眰 ID |

### 5.3 閬垮厤杩囧害宓屽鐨勫師鍒?
涓嶆帹鑽愶細

```json
{
  "data": {
    "responseBody": {
      "code": 429,
      "message": "too many requests",
      "data": null
    }
  }
}
```

鍘熷洜锛?
```text
1. 璋冪敤鏂归渶瑕佸娆¤В鍖咃紝浣跨敤浣撻獙宸€?2. 缃戝叧璋冪敤缁撴灉鍜屼笂娓镐笟鍔＄粨鏋滄贩鍦ㄤ竴璧凤紝璇箟涓嶆竻銆?3. 鍚庣画鍐?gateway_log 鏃惰繕瑕佸啀鎷嗗瓧娈点€?4. Agent Tool 鏇村叧蹇冪粨鏋勫寲浜嬪疄锛岃€屼笉鏄畬鏁村祵濂楀師鏂囥€?```

鎺ㄨ崘鍋氭硶锛?
```text
骞冲彴鑷繁鐨勭姸鎬佹斁鍦ㄥ灞?code/message銆?涓婃父 API 鐨勭姸鎬佹憳瑕佹斁鍦?data.upstreamStatus / data.upstreamCode / data.upstreamMessage銆?涓婃父 API 鐨勪笟鍔¤繑鍥炴暟鎹斁鍦?data.upstreamData銆?瀹屾暣涓婃父鍘熷鍝嶅簲浣撻粯璁や笉杩斿洖锛屽彧鍦?debug/sample 鍦烘櫙灏戦噺杩斿洖鑴辨晱鎽樿銆?```

---

## 6. Mock Provider 鍗曚釜 API 濂戠害

Mock Provider 鍗曚釜涓氬姟 API 淇濈暀涓氬姟璇箟锛屼笉寮鸿濂?Gateway Invoke 璇锋眰浣撱€?
绀轰緥锛?
```http
POST /mock-provider/lecture/register
```

璇锋眰浣擄細

```json
{
  "lectureId": "lec_20260619_ai_001",
  "studentNo": "2023001001",
  "idempotencyKey": "4b6e5f58-1a5a-4d8c-b21c-8aa51f8e7a13",
  "mockScenario": "NORMAL"
}
```

姝ｅ父鍝嶅簲锛?
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "lectureId": "lec_20260619_ai_001",
    "studentNo": "2023001001",
    "registerStatus": "SUCCESS",
    "ticketNo": "ticket_mock_001"
  },
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "requestId": "8f7c6b2e-0f6e-4c2f-9c12-2e6c2b1d9a88",
  "timestamp": "2026-06-19T12:00:00+08:00"
}
```

寮傚父鍝嶅簲锛?
```json
{
  "code": 429,
  "message": "too many requests",
  "data": null,
  "errorCode": "RATE_LIMITED",
  "errorDetail": "lecture register rate limit triggered",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "requestId": "8f7c6b2e-0f6e-4c2f-9c12-2e6c2b1d9a88",
  "timestamp": "2026-06-19T12:00:01+08:00"
}
```

Mock Provider 鍗曚釜 API 鐨?HTTP 鐘舵€佺爜瑙勫垯锛?
```text
HTTP 鐘舵€佺爜 = 妯℃嫙鐨勭湡瀹炲閮?API 缁撴灉銆?body.code = HTTP 鐘舵€佺爜銆?```

---

## 7. API-HUB Gateway Invoke 濂戠害

### 7.1 璁捐鐩殑

Gateway Invoke 鏄?API-HUB 骞冲彴渚х殑缁熶竴澶栭儴 API 璋冪敤鍏ュ彛銆?
Scenario Runner 涓嶅簲鐩存帴璋冪敤 Mock Provider 鐨勫叿浣?API锛岃€屽簲璋冪敤锛?
```http
POST /api/dev/gateway/invoke
```

鐒跺悗鐢?`apihub-server` 鏍规嵁 `apiCode` 鎵惧埌瀵瑰簲澶栭儴 API 閰嶇疆锛屼唬鐞嗚皟鐢?Mock Provider锛屽苟璁板綍姣忎竴娆¤皟鐢ㄥ埌 `gateway_log`銆?
### 7.2 璇锋眰鏍煎紡

Gateway Invoke 璇锋眰搴斿敖閲忎互涓氬姟璋冪敤涓轰腑蹇冿紝涓嶅簲璁╄皟鐢ㄦ柟閲嶅浼?`method/path`銆?
涓嶆帹鑽愶細

```json
{
  "apiCode": "LECTURE_REGISTER",
  "method": "POST",
  "path": "/mock-provider/lecture/register"
}
```

鎺ㄨ崘锛?
```json
{
  "apiCode": "LECTURE_REGISTER",
  "appCode": "LECTURE_PORTAL",
  "mockScenario": "RATE_LIMITED",
  "headers": {
    "Idempotency-Key": "4b6e5f58-1a5a-4d8c-b21c-8aa51f8e7a13"
  },
  "query": {},
  "body": {
    "lectureId": "lec_20260619_ai_001",
    "studentNo": "2023001001",
    "idempotencyKey": "4b6e5f58-1a5a-4d8c-b21c-8aa51f8e7a13"
  },
  "timeoutMs": 3000,
  "includeUpstreamData": true,
  "client": {
    "ip": "10.0.0.12",
    "userAgent": "scenario-runner/1.0"
  },
  "simulation": {
    "scenarioRunId": "sr_20260619_7f3a9c",
    "scenarioId": 2,
    "scenarioKey": "lecture-register-peak",
    "phase": "PEAK",
    "logicalTime": "2026-06-19T12:08:20+08:00",
    "sequence": 128
  }
}
```

瀛楁璇存槑锛?
| 瀛楁 | 璇存槑 |
|---|---|
| `apiCode` | 瑕佽皟鐢ㄧ殑澶栭儴 API 涓氬姟缂栫爜 |
| `appCode` | 妯℃嫙璋冪敤鏂瑰簲鐢ㄧ紪鐮?|
| `mockScenario` | 浼犵粰 Mock Provider 鐨勬ā鎷熷満鏅?|
| `headers/query/body` | 涓氬姟璇锋眰鍙傛暟 |
| `timeoutMs` | 璋冪敤澶栭儴 API 瓒呮椂鏃堕棿 |
| `includeUpstreamData` | 鏄惁杩斿洖澶栭儴 API 鐨勪笟鍔℃暟鎹紱鍗曟璋冭瘯寤鸿 true锛屾壒閲忓満鏅皟鐢ㄥ缓璁?false |
| `client` | 妯℃嫙瀹㈡埛绔俊鎭?|
| `simulation` | 鍦烘櫙杩愯涓婁笅鏂囷紝鐢?Scenario Runner 濉厖 |

### 7.3 杩斿洖鏍煎紡

Gateway Invoke 鐨勮繑鍥炲簲鍚屾椂鍖呭惈涓ょ被淇℃伅锛?
```text
1. API-HUB 骞冲彴渚ц皟鐢ㄤ簨瀹烇細鏄惁璋冪敤鎴愬姛銆佽€楁椂銆佹棩蹇?ID銆乀race 绛夈€?2. 澶栭儴 API 鐪熷疄杩斿洖鎽樿锛氫笂娓哥姸鎬佺爜銆佷笂娓告秷鎭€佷笂娓镐笟鍔℃暟鎹€?```

涓轰簡閬垮厤澶氬眰宓屽锛屼笉杩斿洖瀹屾暣 `responseBody.code/message/data`锛岃€屾槸鎷嗘垚鎵佸钩瀛楁锛?
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "gatewayLogId": 12345,
    "apiCode": "LECTURE_REGISTER",
    "appCode": "LECTURE_PORTAL",
    "ok": false,
    "upstreamStatus": 429,
    "upstreamCode": 429,
    "upstreamMessage": "too many requests",
    "upstreamData": null,
    "errorCode": "RATE_LIMITED",
    "mockScenario": "RATE_LIMITED",
    "latencyMs": 86,
    "scenarioRunId": "sr_20260619_7f3a9c",
    "scenarioId": 2,
    "scenarioKey": "lecture-register-peak",
    "phase": "PEAK"
  },
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "requestId": "8f7c6b2e-0f6e-4c2f-9c12-2e6c2b1d9a88",
  "timestamp": "2026-06-19T12:08:20+08:00"
}
```

姝ｅ父璋冪敤鏃讹紝`upstreamData` 淇濆瓨澶栭儴 API 鐨勫疄闄呬笟鍔¤繑鍥炴暟鎹細

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "gatewayLogId": 12346,
    "apiCode": "LECTURE_REGISTER",
    "appCode": "LECTURE_PORTAL",
    "ok": true,
    "upstreamStatus": 200,
    "upstreamCode": 200,
    "upstreamMessage": "success",
    "upstreamData": {
      "lectureId": "lec_20260619_ai_001",
      "studentNo": "2023001001",
      "registerStatus": "SUCCESS",
      "ticketNo": "ticket_mock_001"
    },
    "errorCode": null,
    "mockScenario": "NORMAL",
    "latencyMs": 72
  },
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "requestId": "8f7c6b2e-0f6e-4c2f-9c12-2e6c2b1d9a88",
  "timestamp": "2026-06-19T12:08:20+08:00"
}
```

### 7.4 涓婃父涓氬姟鏁版嵁鍘诲摢浜?
涓婃父 API 杩斿洖鐨勬暟鎹笉浼氫涪澶憋紝澶勭悊瑙勫垯濡備笅锛?
| 鏁版嵁绫诲瀷 | 杩斿洖浣嶇疆 | 鏄惁榛樿杩斿洖 | 璇存槑 |
|---|---|---|---|
| 涓婃父鐘舵€佺爜 | `data.upstreamStatus` | 鏄?| 渚嬪 200 / 403 / 429 / 500 |
| 涓婃父涓氬姟 code | `data.upstreamCode` | 鏄?| 璇诲彇涓婃父鍝嶅簲浣?`code` |
| 涓婃父鎻愮ず淇℃伅 | `data.upstreamMessage` | 鏄?| 璇诲彇涓婃父鍝嶅簲浣?`message` |
| 涓婃父涓氬姟 data | `data.upstreamData` | 鍙帶 | `includeUpstreamData=true` 鏃惰繑鍥?|
| 涓婃父瀹屾暣鍘熷鍝嶅簲浣?| 涓嶅缓璁洿鎺ヨ繑鍥?| 鍚?| 鍙厑璁?debug 鎴栨牱渚嬩腑杩斿洖鑴辨晱鎽樿 |

璁捐鍘熷洜锛?
```text
Gateway Invoke 鏄钩鍙拌皟鐢ㄥ叆鍙ｏ紝涓嶆槸涓氬姟 API 鐨勯€忔槑杞彂鎺ュ彛銆?瀹冨繀椤昏繑鍥炶皟鐢ㄤ簨瀹烇紝涔熻淇濈暀涓婃父涓氬姟缁撴灉銆?鍥犳閲囩敤 upstreamData 淇濆瓨鐪熷疄涓氬姟 data锛岀敤鎵佸钩瀛楁淇濆瓨涓婃父鐘舵€佹憳瑕併€?```

鎺ㄨ崘榛樿鍊硷細

| 璋冪敤鏉ユ簮 | `includeUpstreamData` 榛樿鍊?|
|---|---|
| 浜哄伐璋冭瘯 / Apifox / 鍗曟 Gateway Invoke | `true` |
| Scenario Runner 鎵归噺璋冪敤 | `false` |
| Scenario Runner sampleCalls | 鍙灏戦噺鏍蜂緥淇濈暀鑴辨晱 upstreamData |

### 7.5 澶栧眰 code 涓庝笂娓哥姸鎬佺爜鐨勫叧绯?
Gateway Invoke 鏄€滃钩鍙拌皟鐢ㄥ姩浣溾€濈殑鎺ュ彛锛屽洜姝わ細

```text
鍙 Gateway Invoke 鏈韩鎵ц鎴愬姛锛屽灞?code=200銆?澶栭儴 API 杩斿洖 403/429/500 绛夛紝鏀惧湪 data.upstreamStatus銆?```

鍙湁浠ヤ笅鎯呭喌鎵嶅簲璁?Gateway Invoke 澶栧眰澶辫触锛?
```text
apiCode 涓嶅瓨鍦ㄣ€?appCode 鏃犺皟鐢ㄦ潈闄愩€?璇锋眰鍙傛暟闈炴硶銆?Mock Provider 鍦板潃鏈厤缃€?Gateway Invoke 鑷韩鎵ц寮傚父銆?鏁版嵁搴撳啓 gateway_log 澶辫触涓旀湰杞姹傚己涓€鑷磋褰曘€?```

---

## 8. Scenario Runner 璋冪敤濂戠害

### 8.1 璁捐鐩殑

Scenario Runner 鏄€滃満鏅祦閲忚繍琛屽櫒鈥濓紝璐熻矗鎸変笟鍔″満鏅敓鎴愪竴鎵硅皟鐢ㄨ鍒掋€傚畠鍙互鏀惧湪 `apihub-mock-provider` 妯″潡涓紝浣嗘瘡涓€娆″叿浣?API 璋冪敤蹇呴』缁忚繃 `apihub-server` 鐨?Gateway Invoke銆?
### 8.2 鍦烘櫙瀹氫箟鏌ヨ鎺ュ彛

Scenario Runner 涓嶅缓璁璋冪敤鏂圭洿鎺ヨ緭鍏?`scenarioCode` 鎴?`scenarioRunName`銆?
鎺ㄨ崘鍏堟彁渚涘満鏅洰褰曟煡璇細

```http
GET /mock-provider/scenarios
```

杩斿洖绀轰緥锛?
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "scenarios": [
      {
        "scenarioId": 1,
        "scenarioKey": "normal-day",
        "scenarioName": "鏅€氬伐浣滄棩",
        "description": "妯℃嫙鏅€氬伐浣滄棩绋冲畾娴侀噺锛屽紓甯哥巼杈冧綆銆?,
        "defaultLogicalDurationSeconds": 3600,
        "recommendedTimeScale": 60
      },
      {
        "scenarioId": 2,
        "scenarioKey": "lecture-register-peak",
        "scenarioName": "璁插骇鎶ュ悕楂樺嘲",
        "description": "妯℃嫙璁插骇鎶ュ悕寮€鏀惧悗鐨勬煡璇€佺櫥褰曘€佹姤鍚嶆彁浜ら珮宄般€?,
        "defaultLogicalDurationSeconds": 1800,
        "recommendedTimeScale": 60
      }
    ]
  },
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "requestId": "8f7c6b2e-0f6e-4c2f-9c12-2e6c2b1d9a88",
  "timestamp": "2026-06-19T12:00:00+08:00"
}
```

璋冪敤鏂硅繍琛屽満鏅椂锛屽彧闇€瑕佷紶 `scenarioId`銆?
### 8.3 鍦烘櫙杩愯鍚姩鎺ュ彛

鎺ㄨ崘璁捐鎴愯祫婧愬紡鎺ュ彛锛?
```http
POST /mock-provider/scenario-runs
```

鍚箟锛?
```text
鍒涘缓涓€娆″満鏅繍琛屻€?```

璇锋眰绀轰緥锛?
```json
{
  "scenarioId": 2,
  "targetGatewayBaseUrl": "http://localhost:8080",
  "waitForCompletion": false,
  "loadProfile": {
    "logicalDurationSeconds": 1800,
    "timeScale": 60,
    "rampUpSeconds": 300,
    "steadySeconds": 1200,
    "rampDownSeconds": 300,
    "baseRps": 1,
    "peakRps": 20,
    "maxConcurrency": 30
  },
  "network": {
    "delayMinMs": 10,
    "delayMaxMs": 120,
    "timeoutMs": 3000
  },
  "sampling": {
    "randomSeed": 20260619,
    "sampleLimit": 20
  },
  "overrides": {
    "totalRequests": 600
  },
  "note": "鏈湴寮€鍙戞紨绀猴紝鍙€?
}
```

瀛楁璇存槑锛?
| 瀛楁 | 鏄惁蹇呴渶 | 璇存槑 |
|---|---|---|
| `scenarioId` | 蹇呴渶 | 鏈嶅姟绔凡鏈夊満鏅畾涔?ID |
| `targetGatewayBaseUrl` | 蹇呴渶 | API-HUB Gateway Invoke 鎵€鍦ㄥ湴鍧€ |
| `waitForCompletion` | 鍙€?| 榛樿 false锛岄暱鍦烘櫙搴斿紓姝ヨ繑鍥?|
| `loadProfile` | 鍙€?| 瑕嗙洊鍦烘櫙榛樿璐熻浇妯″瀷 |
| `network` | 鍙€?| 妯℃嫙缃戠粶寤惰繜鍜岃秴鏃堕厤缃?|
| `sampling` | 鍙€?| 闅忔満绉嶅瓙鍜岃繑鍥炴牱渚嬫暟閲?|
| `overrides` | 鍙€?| 鏈湴婕旂ず鐢ㄨ鐩栭」 |
| `note` | 鍙€?| 浜哄伐澶囨敞锛屼笉鍙備笌璋冨害閫昏緫 |

涓嶆帹鑽愬湪璇锋眰涓紶 `scenarioRunName`锛?
```json
{
  "scenarioCode": "LECTURE_REGISTER_PEAK",
  "scenarioRunName": "2026-06-19 璁插骇鎶ュ悕楂樺嘲妯℃嫙"
}
```

鍘熷洜锛?
```text
1. 鍦烘櫙鍚嶇О搴旂敱鏈嶅姟绔牴鎹?scenarioId 缁存姢銆?2. 璇锋眰鏂瑰彧闇€瑕佹寚瀹氳杩愯鍝釜鍦烘櫙銆?3. runName 鏄睍绀轰俊鎭紝涓嶆槸鎵ц鏉′欢銆?4. 濡傛灉纭疄闇€瑕佷汉宸ュ娉紝鍙娇鐢?note锛屽彲閫夈€?```

### 8.4 寮傛鍚姩杩斿洖鏍煎紡

瀵逛簬闀挎椂闂村満鏅紝`POST /mock-provider/scenario-runs` 搴旂珛鍗宠繑鍥炲惎鍔ㄧ粨鏋溿€?
鎺ㄨ崘 HTTP 202锛?
```json
{
  "code": 202,
  "message": "accepted",
  "data": {
    "scenarioRunId": "sr_20260619_7f3a9c",
    "scenarioId": 2,
    "scenarioKey": "lecture-register-peak",
    "scenarioName": "璁插骇鎶ュ悕楂樺嘲",
    "status": "RUNNING",
    "statusUrl": "/mock-provider/scenario-runs/sr_20260619_7f3a9c",
    "resultUrl": "/mock-provider/scenario-runs/sr_20260619_7f3a9c/result",
    "startedAt": "2026-06-19T12:00:00+08:00",
    "estimatedLogicalDurationSeconds": 1800,
    "estimatedActualDurationSeconds": 30,
    "timeScale": 60
  },
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "requestId": "8f7c6b2e-0f6e-4c2f-9c12-2e6c2b1d9a88",
  "timestamp": "2026-06-19T12:00:00+08:00"
}
```

### 8.5 鏌ヨ杩愯鐘舵€?
```http
GET /mock-provider/scenario-runs/{scenarioRunId}
```

杩愯涓繑鍥烇細

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "scenarioRunId": "sr_20260619_7f3a9c",
    "scenarioId": 2,
    "scenarioKey": "lecture-register-peak",
    "scenarioName": "璁插骇鎶ュ悕楂樺嘲",
    "status": "RUNNING",
    "progress": {
      "totalPlannedRequests": 600,
      "finishedRequests": 180,
      "failedInvokeCount": 23,
      "currentPhase": "RAMP_UP",
      "progressPercent": 30
    },
    "startedAt": "2026-06-19T12:00:00+08:00",
    "finishedAt": null
  },
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "requestId": "8f7c6b2e-0f6e-4c2f-9c12-2e6c2b1d9a88",
  "timestamp": "2026-06-19T12:00:10+08:00"
}
```

### 8.6 鏌ヨ杩愯缁撴灉

鍦烘櫙杩愯瀹屾垚鍚庯紝缁撴灉搴旀寔涔呭寲锛岀劧鍚庨€氳繃鏌ヨ鎺ュ彛鑾峰彇銆?
```http
GET /mock-provider/scenario-runs/{scenarioRunId}/result
```

瀹屾垚鍚庤繑鍥烇細

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "scenarioRunId": "sr_20260619_7f3a9c",
    "scenarioId": 2,
    "scenarioKey": "lecture-register-peak",
    "scenarioName": "璁插骇鎶ュ悕楂樺嘲",
    "status": "COMPLETED",
    "totalRequests": 600,
    "successCount": 451,
    "failCount": 149,
    "logicalDurationSeconds": 1800,
    "actualDurationMs": 30000,
    "timeScale": 60,
    "apiDistribution": {
      "AUTH_LOGIN": 150,
      "LECTURE_LIST": 150,
      "LECTURE_REGISTER": 300
    },
    "statusDistribution": {
      "200": 451,
      "409": 32,
      "429": 86,
      "503": 31
    },
    "mockScenarioDistribution": {
      "NORMAL": 451,
      "RATE_LIMITED": 86,
      "DUPLICATE_REQUEST": 32,
      "SERVICE_BUSY": 31
    },
    "phaseDistribution": {
      "RAMP_UP": 100,
      "PEAK": 400,
      "RAMP_DOWN": 100
    },
    "latencySummary": {
      "avgLatencyMs": 96,
      "p95LatencyMs": 680,
      "p99LatencyMs": 1200
    },
    "sampleCallsUrl": "/mock-provider/scenario-runs/sr_20260619_7f3a9c/sample-calls"
  },
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "requestId": "8f7c6b2e-0f6e-4c2f-9c12-2e6c2b1d9a88",
  "timestamp": "2026-06-19T12:30:00+08:00"
}
```

### 8.7 鏌ヨ鏍蜂緥璋冪敤

涓嶅缓璁湪缁撴灉鎺ュ彛閲岃繑鍥炲ぇ閲忔瘡娆¤皟鐢ㄨ鎯呫€? 
濡傛灉闇€瑕佹煡鐪嬫牱渚嬭皟鐢紝浣跨敤鍗曠嫭鎺ュ彛锛?
```http
GET /mock-provider/scenario-runs/{scenarioRunId}/sample-calls?limit=20
```

杩斿洖绀轰緥锛?
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "scenarioRunId": "sr_20260619_7f3a9c",
    "calls": [
      {
        "sequence": 128,
        "apiCode": "LECTURE_REGISTER",
        "mockScenario": "RATE_LIMITED",
        "upstreamStatus": 429,
        "upstreamMessage": "too many requests",
        "latencyMs": 86,
        "gatewayLogId": 12345,
        "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
        "requestId": "8f7c6b2e-0f6e-4c2f-9c12-2e6c2b1d9a88"
      }
    ]
  },
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "requestId": "8f7c6b2e-0f6e-4c2f-9c12-2e6c2b1d9a88",
  "timestamp": "2026-06-19T12:30:00+08:00"
}
```

### 8.8 鍦烘櫙杩愯缁撴灉鎸佷箙鍖?
Scenario Runner 搴旀寔涔呭寲浠ヤ笅淇℃伅锛屼究浜庤繍琛屽畬鎴愬悗鏌ヨ鍜屽鐜帮細

| 鏁版嵁 | 璇存槑 |
|---|---|
| 鍦烘櫙杩愯鍏冧俊鎭?| `scenarioRunId`銆乣scenarioId`銆佺姸鎬併€佸紑濮嬫椂闂淬€佺粨鏉熸椂闂?|
| 杩愯鍙傛暟蹇収 | 鏈瀹為檯浣跨敤鐨?`loadProfile`銆乣network`銆乣sampling`銆乣overrides` |
| 姹囨€荤粨鏋?| 璇锋眰鏁般€佹垚鍔熷け璐ユ暟銆佺姸鎬佺爜鍒嗗竷銆丄PI 鍒嗗竷銆侀樁娈靛垎甯冦€佸欢杩熸憳瑕?|
| 鏍蜂緥璋冪敤 | 灏戦噺鑴辨晱鏍蜂緥锛屽寘鍚?`gatewayLogId`銆乣traceId`銆乣requestId` |
| 閿欒鎽樿 | 璋冪敤 Gateway Invoke 澶辫触銆佽秴鏃躲€佷腑鏂瓑 Runner 渚ч敊璇?|

瀹炵幇灞傞潰鍙互鍏堢敤杞婚噺瀛樺偍锛屼絾濂戠害灞傞潰鎸夆€滃彲鏌ヨ銆佸彲澶嶇幇銆佸彲鍥炴斁鈥濊璁°€?
娉ㄦ剰锛?
```text
姣忎竴娆″叿浣撳閮?API 璋冪敤鐨勪簨瀹炴棩蹇椾粛鐒剁敱 apihub-server 鐨?Gateway Invoke 鍐欏叆 gateway_log銆?Scenario Runner 鑷繁鍙繚瀛樺満鏅繍琛屽厓淇℃伅鍜屾眹鎬荤粨鏋滐紝涓嶆浛浠?gateway_log銆?```

---

## 9. Gateway Log 鏄犲皠鍘熷垯

姣忎竴娆?Gateway Invoke 閮藉簲鐢熸垚涓€鏉?`gateway_log`銆?
鎺ㄨ崘鏄犲皠锛?
| 璋冪敤浜嬪疄 | `gateway_log` 瀛楁鎴栧瓨鍌ㄤ綅缃?|
|---|---|
| `apiCode` | API 缂栫爜瀛楁 |
| `appCode` | 璋冪敤鏂瑰簲鐢ㄥ瓧娈?|
| `method` | 鐢?`apiCode` 瀵瑰簲鐨?API 閰嶇疆纭畾 |
| `path/upstreamUrl` | 鐢?`apiCode` 瀵瑰簲鐨?API 閰嶇疆纭畾 |
| `upstreamStatus` | 鐘舵€佺爜瀛楁 |
| `latencyMs` | 寤惰繜瀛楁 |
| `traceId` | Trace 瀛楁 |
| `requestId` | 璇锋眰 ID 瀛楁锛岃嫢鏃犲垯鏀惧叆鎵╁睍 JSON |
| `scenarioRunId` | 鎵╁睍 JSON 鎴?trace 鍏宠仈瀛楁 |
| `scenarioId` | 鎵╁睍 JSON |
| `scenarioKey` | 鎵╁睍 JSON |
| `phase` | 鎵╁睍 JSON |
| `mockScenario` | 閿欒淇℃伅瀛楁鎴栨墿灞?JSON |
| `errorCode` | 閿欒鐮佸瓧娈垫垨鎵╁睍 JSON |
| `upstreamMessage` | 閿欒淇℃伅瀛楁 |
| `requestBody` 鎽樿 | 鎵╁睍 JSON锛岄伩鍏嶄繚瀛樻晱鎰熷瓧娈?|
| `upstreamData` 鎽樿 | 鎵╁睍 JSON锛岄伩鍏嶈繃澶?|

绾︽潫锛?
```text
涓嶄繚瀛樼湡瀹?secret銆?涓嶄繚瀛樼湡瀹?token銆?涓嶄繚瀛樼湡瀹?password銆?璇锋眰浣撳拰鍝嶅簲浣撳彧淇濆瓨鎽樿鎴栬劚鏁忕増鏈€?榛樿涓嶄繚瀛樺畬鏁翠笂娓稿搷搴斾綋銆?```

---

## 10. 涓庡叾浠栨枃妗ｇ殑鍏崇郴

| 鏂囨。 | 鍏崇郴 |
|---|---|
| `06_EXTERNAL_API_CATALOG.md` | 瀹氫箟鏈夊摢浜涘閮?API锛屾湰鏂囨。瀹氫箟濡備綍璋冪敤瀹冧滑 |
| `07_MOCK_PROVIDER_AND_TRAFFIC_SIMULATION.md` | 浣跨敤鏈枃妗ｇ殑 Gateway Invoke 濂戠害鏉ヨ璁?Scenario Runner |
| `08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md` | Agent 鍚庣画閫氳繃 Tools 鏌ヨ Gateway Invoke 浜х敓鐨勪簨瀹炴暟鎹?|
| `09_VALIDATION_AND_SMOKE_GUIDE.md` | 楠岃瘉鏈枃妗ｄ腑鐨?Gateway Invoke銆丮ock Provider銆丼cenario Runner 璋冪敤鏍煎紡鏄惁姝ｇ‘ |
