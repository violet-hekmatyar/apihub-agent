# Mock Provider 涓庡満鏅祦閲忔ā鎷熻璁?
## 0. 鏂囨。瀹氫綅

鏈枃妗ｅ畾涔?`apihub-mock-provider` 鐨勬ā鍧楄竟鐣屻€佸崟涓閮?API 鐨勬ā鎷熸柟寮忋€丼cenario Runner 鐨勭湡瀹炴祦閲忔ā鍨嬶紝浠ュ強瀹冧笌 `apihub-server` Gateway Invoke 鐨勫叧绯汇€?
鏈枃妗ｄ笉瀹氫箟缁熶竴杩斿洖鏍煎紡鐨勫畬鏁村瓧娈碉紝涓嶅畾涔?Agent 璇婃柇娴佺▼锛屼笉瀹氫箟 smoke 鑴氭湰缁嗚妭銆?
鐩稿叧鏂囨。锛?
```text
05_API_INVOKE_CONTRACT.md锛氱粺涓€璋冪敤濂戠害銆両D銆乀race銆丟ateway Invoke銆?06_EXTERNAL_API_CATALOG.md锛氬閮?API 涓氬姟鐩綍銆?08_AGENT_DIAGNOSIS_AND_EVAL_FLOW.md锛歍ool Chain 鍜?Agent Run銆?09_VALIDATION_AND_SMOKE_GUIDE.md锛氶獙鏀舵柟寮忋€?```

---

## 1. 妯″潡鑱岃矗

### 1.1 `apihub-mock-provider`

璐熻矗锛?
```text
1. 鎻愪緵 7 涓閮ㄤ笟鍔?API 鐨勫崟娆?mock 琛屼负锛?2. 榛樿杩斿洖 NORMAL 姝ｅ父缁撴灉锛?3. 鏀寔鏄惧紡寮傚父鍦烘櫙锛?4. 鍚庣画鎻愪緵 Scenario Runner锛岀敤浜庣敓鎴愮湡瀹炰笟鍔℃祦閲忥紱
5. Scenario Runner 璋冪敤 apihub-server Gateway Invoke锛岃€屼笉鏄洿鎺ョ粫杩?API-HUB 骞冲彴銆?```

涓嶈礋璐ｏ細

```text
涓嶅啓 apihub_agent 鏁版嵁搴擄紱
涓嶇洿鎺ュ啓 gateway_log锛?涓嶈仛鍚?api_call_stat_hourly锛?涓嶇敓鎴?alert_event锛?涓嶆墽琛?Agent / Tool / Report锛?涓嶈皟鐢?LLM / DashScope / Milvus / Embedding銆?```

### 1.2 `apihub-server`

璐熻矗锛?
```text
1. 鎻愪緵 Gateway Invoke锛?2. 鏍规嵁 apiCode 璋冪敤 Mock Provider 鍗曚釜 API锛?3. 璁板綍姣忎竴娆¤皟鐢ㄥ埌 gateway_log锛?4. 鍚庣画鑱氬悎缁熻锛?5. 鍚庣画鐢熸垚鍛婅锛?6. 閫氳繃 Tools 鍜?Agent 璇婃柇杩欎簺浜嬪疄銆?```

---

## 2. 鍗曚釜澶栭儴 API 妯℃嫙

鎵€鏈?API 榛樿鍦烘櫙涓?`NORMAL`銆?
寮傚父鍦烘櫙瑙﹀彂浼樺厛绾э細

```text
X-Mock-Scenario > body/query mockScenario > NORMAL
```

| API Code | Mock Path | NORMAL | 寮傚父鍦烘櫙 |
|---|---|---|---|
| `AUTH_LOGIN` | `POST /mock-provider/auth/login` | 杩斿洖 mock token銆佸鐢熶俊鎭€佽繃鏈熸椂闂?| `SIGNATURE_MISMATCH`, `TOKEN_EXPIRED`, `TIMESTAMP_EXPIRED`, `NONCE_REPLAY`, `UNKNOWN_APP` |
| `COURSE_TODAY` | `GET /mock-provider/course/today` | 杩斿洖鍥哄畾璇捐〃 | `SLOW_RESPONSE`, `DOWNSTREAM_SLOW`, `COURSE_SYSTEM_TIMEOUT`, `CACHE_MISS` |
| `LECTURE_LIST` | `GET /mock-provider/lecture/list` | 杩斿洖鍥哄畾璁插骇鍒楄〃 | `HOT_READ`, `SLOW_RESPONSE`, `SERVICE_BUSY` |
| `LECTURE_REGISTER` | `POST /mock-provider/lecture/register` | 杩斿洖鎶ュ悕鎴愬姛鍜?mock ticket | `RATE_LIMITED`, `DUPLICATE_REQUEST`, `SOLD_OUT`, `IDEMPOTENCY_KEY_MISSING`, `SLOW_RESPONSE`, `SERVICE_BUSY` |
| `CAMPUS_NOTICE` | `GET /mock-provider/notice/list` | 杩斿洖鍥哄畾閫氱煡鍒楄〃 | `HOT_NOTICE`, `CACHE_MISS`, `SLOW_RESPONSE`, `SERVICE_BUSY` |
| `VENUE_RESERVE` | `POST /mock-provider/venue/reserve` | 杩斿洖棰勭害鎴愬姛鍜?mock reserveNo | `RESERVATION_CONFLICT`, `DUPLICATE_REQUEST`, `IDEMPOTENCY_KEY_MISSING`, `RATE_LIMITED`, `SLOW_RESPONSE` |
| `LIBRARY_BORROW` | `GET /mock-provider/library/borrow` | 杩斿洖鍥哄畾鍊熼槄璁板綍 | `DOWNSTREAM_TIMEOUT`, `DEPENDENCY_UNAVAILABLE`, `SLOW_RESPONSE`, `SERVICE_ERROR` |

璁捐鍘熷垯锛?
```text
Mock Provider 鍗曚釜 API 涓嶉殢鏈哄紓甯搞€?闅忔満鍜屾瘮渚嬬敱 Scenario Runner 鎺у埗銆?杩欐牱 smoke 娴嬭瘯绋冲畾锛屽満鏅ā鎷熷彲澶嶇幇銆?```

---

## 3. Scenario Runner 鎬讳綋璁捐

Scenario Runner 鏄満鏅祦閲忚繍琛屽櫒锛岀敤浜庢ā鎷熺湡瀹炶皟鐢ㄦ柟鍦ㄤ笟鍔″満鏅笅瀵?API-HUB 鍙戣捣涓€鎵硅姹傘€?
鎺ㄨ崘鎺ュ彛锛?
```http
POST /mock-provider/scenarios/run
```

璋冪敤閾撅細

```text
POST /mock-provider/scenarios/run
        鈹?        鈻?mock-provider 鐢熸垚鍦烘櫙璋冪敤璁″垝
        鈹?        鈻?澶氱嚎绋嬭皟鐢?apihub-server Gateway Invoke
POST /api/dev/gateway/invoke
        鈹?        鈻?apihub-server 璋冪敤 mock-provider 鍗曚釜 API
        鈹?        鈻?apihub-server 璁板綍 gateway_log
        鈹?        鈻?mock-provider 姹囨€?scenario run 缁撴灉
```

鍏抽敭绾︽潫锛?
```text
Scenario Runner 鍙互鏀惧湪 mock-provider 涓€?浣嗘瘡涓€娆″叿浣?API 璋冪敤蹇呴』缁忚繃 apihub-server Gateway Invoke銆?Scenario Runner 涓嶇洿鎺ュ啓 gateway_log銆?```

---

Scenario Runner 后续调用 `/api/dev/gateway/invoke` 时应使用已授权的默认 appCode，避免授权校验失败：

| API Code | Default App Code |
|---|---|
| `AUTH_LOGIN` | `COURSE_HELPER` |
| `COURSE_TODAY` | `COURSE_HELPER` |
| `LECTURE_LIST` | `LECTURE_PORTAL` |
| `LECTURE_REGISTER` | `LECTURE_PORTAL` |
| `CAMPUS_NOTICE` | `STUDENT_SERVICE` |
| `VENUE_RESERVE` | `CLUB_ACTIVITY` |
| `LIBRARY_BORROW` | `LIBRARY_MINI` |

## 4. 鐪熷疄娴侀噺妯℃嫙鍘熷垯

### 4.1 涓嶅仛鍏ㄥ紓甯告祦閲?
鐪熷疄绯荤粺涓紝澶у鏁拌姹傚簲鏄甯哥殑銆?
鎺ㄨ崘姣斾緥锛?
```text
鏅€氬伐浣滄棩锛氭甯?96%锝?9%锛屽紓甯?1%锝?%銆?涓氬姟楂樺嘲锛氭牳蹇?API 寮傚父鐜囧崌楂橈紝浣嗘甯歌姹備粛鍗犲鏁般€?渚濊禆寮傚父锛氬彈褰卞搷 API 5xx/504 鍗囬珮锛屽叾浠?API 鍩烘湰姝ｅ父銆?```

### 4.2 寮傚父鏉ユ簮鍒嗕袱绫?
#### 鏉′欢椹卞姩寮傚父

鐢卞満鏅喅瀹氾紝渚嬪锛?
```text
璁插骇鎶ュ悕楂樺嘲 鈫?LECTURE_REGISTER 429 / 閲嶅璇锋眰 / 鍚嶉婊″鍔犮€?鐧诲綍寮傚父 鈫?AUTH_LOGIN 403 / 401 澧炲姞銆?鍥句功棣嗕緷璧栧紓甯?鈫?LIBRARY_BORROW 503 / 504 澧炲姞銆?```

#### 灏忔鐜囬殢鏈哄紓甯?
鍗充娇鍦ㄦ櫘閫氬満鏅紝涔熷彲浠ユ湁鏋佸皬姒傜巼寮傚父锛?
```text
1%锝?% 鎱㈠搷搴斻€佺紦瀛?miss銆佸伓鍙?5xx銆?```

瑕佹眰锛?
```text
蹇呴』鏀寔 randomSeed锛屼繚璇佸彲澶嶇幇銆?涓嶈兘璁?smoke 娴嬭瘯渚濊禆涓嶇ǔ瀹氶殢鏈虹粨鏋溿€?```

### 4.3 闇€瑕?Ramp-up / Peak / Ramp-down

涓嶈妯℃嫙鎴愪竴鐬棿鍏ㄩ儴楂樺苟鍙戙€?
鏇寸湡瀹炵殑涓氬姟楂樺嘲锛?
```text
浣庢祦閲?鈫?ramp-up 娓愬彉鍗囬珮
鈫?peak 楂樺嘲鎸佺画
鈫?ramp-down 鍥炶惤
```

璁插骇鎶ュ悕楂樺嘲绀轰緥锛?
```text
閫昏緫鎸佺画鏃堕棿锛?0 鍒嗛挓
鍓?5 鍒嗛挓锛氫粠鏅€氭祦閲忛€愭鍗囧埌楂樺嘲
涓棿 20 鍒嗛挓锛氶珮宄版寔缁?鏈€鍚?5 鍒嗛挓锛氶€愭鍥炶惤
```

涓轰簡婕旂ず鏁堢巼锛屾敮鎸?`timeScale`锛?
```text
durationSeconds=1800锛宼imeScale=60
琛ㄧず閫昏緫妯℃嫙 30 鍒嗛挓锛屽疄闄呯害 30 绉掕窇瀹屻€?```

---

## 5. Scenario Runner 璇锋眰鍙傛暟

鎺ㄨ崘璇锋眰浣擄細

```json
{
  "scenarioCode": "LECTURE_REGISTER_PEAK",
  "scenarioRunName": "2026-06-19 璁插骇鎶ュ悕楂樺嘲妯℃嫙",
  "targetGatewayBaseUrl": "http://localhost:8080",
  "durationSeconds": 1800,
  "timeScale": 60,
  "rampUpSeconds": 300,
  "peakSeconds": 1200,
  "rampDownSeconds": 300,
  "totalRequests": 600,
  "concurrency": 20,
  "randomSeed": 20260619,
  "networkDelayMinMs": 10,
  "networkDelayMaxMs": 120,
  "timeoutMs": 3000,
  "sampleLimit": 20
}
```

鍙傛暟璇存槑锛?
| 鍙傛暟 | 鍚箟 | 寤鸿 |
|---|---|---|
| `scenarioCode` | 鍦烘櫙缂栫爜 | 蹇呭～ |
| `targetGatewayBaseUrl` | API-HUB Gateway Invoke 鎵€鍦ㄦ湇鍔?| 榛樿 `http://localhost:8080` |
| `durationSeconds` | 閫昏緫鎸佺画鏃堕棿 | 蹇呭～鎴栭粯璁?|
| `timeScale` | 鏃堕棿鍘嬬缉姣斾緥 | 婕旂ず鐜鎺ㄨ崘鏀寔 |
| `rampUpSeconds` | 娓愬崌闃舵閫昏緫鏃堕暱 | 楂樺嘲鍦烘櫙寤鸿鏀寔 |
| `peakSeconds` | 楂樺嘲闃舵閫昏緫鏃堕暱 | 楂樺嘲鍦烘櫙寤鸿鏀寔 |
| `rampDownSeconds` | 鍥炶惤闃舵閫昏緫鏃堕暱 | 楂樺嘲鍦烘櫙寤鸿鏀寔 |
| `totalRequests` | 鎬昏姹傞噺 | 蹇呭～ |
| `concurrency` | 骞跺彂绾跨▼鏁?| 蹇呭～ |
| `randomSeed` | 闅忔満绉嶅瓙 | 鎺ㄨ崘 |
| `networkDelayMinMs` | 瀹㈡埛绔晶鏈€灏忕綉缁滄姈鍔?| 鎺ㄨ崘 |
| `networkDelayMaxMs` | 瀹㈡埛绔晶鏈€澶х綉缁滄姈鍔?| 鎺ㄨ崘 |
| `timeoutMs` | 璋冪敤 Gateway Invoke 瓒呮椂鏃堕棿 | 鎺ㄨ崘 |
| `sampleLimit` | 杩斿洖鏍蜂緥璋冪敤鏁伴噺 | 鎺ㄨ崘 |

---

## 6. 鎺ㄨ崘鍦烘櫙閰嶇疆

### 6.1 `NORMAL_DAY`

鏅€氬伐浣滄棩锛岀敤浜庣敓鎴愬熀绾挎祦閲忋€?
| API | 娴侀噺鏉冮噸 | NORMAL | 寮傚父 |
|---|---:|---:|---:|
| `AUTH_LOGIN` | 20% | 97% | 3% |
| `COURSE_TODAY` | 25% | 96% | 4% |
| `CAMPUS_NOTICE` | 20% | 98% | 2% |
| `LECTURE_LIST` | 15% | 98% | 2% |
| `LIBRARY_BORROW` | 20% | 96% | 4% |

鐗圭偣锛?
```text
鎸佺画鏃堕棿杈冮暱銆?骞跺彂杈冧綆銆?寮傚父鐜囦綆銆?閫傚悎浣滀负绋冲畾鍩虹嚎銆?```

### 6.2 `LECTURE_REGISTER_PEAK`

璁插骇鎶ュ悕寮€鏀剧獥鍙ｃ€?
| API | 娴侀噺鏉冮噸 | NORMAL | 寮傚父 |
|---|---:|---:|---:|
| `AUTH_LOGIN` | 25% | 92% | 8% |
| `LECTURE_LIST` | 25% | 95% | 5% |
| `LECTURE_REGISTER` | 50% | 72% | 28% |

`LECTURE_REGISTER` 寮傚父缁嗗垎锛?
| 寮傚父 | 姣斾緥 |
|---|---:|
| `RATE_LIMITED` | 14% |
| `DUPLICATE_REQUEST` | 6% |
| `SOLD_OUT` | 5% |
| `SLOW_RESPONSE` | 3% |

闃舵寤鸿锛?
```text
閫昏緫鎬绘椂闀?30 鍒嗛挓銆?5 鍒嗛挓 ramp-up銆?20 鍒嗛挓 peak銆?5 鍒嗛挓 ramp-down銆?```

### 6.3 `AUTH_FAILURE_SPIKE`

缁熶竴鐧诲綍澶辫触寮傚父鍗囬珮銆?
| API | 娴侀噺鏉冮噸 | NORMAL | 寮傚父 |
|---|---:|---:|---:|
| `AUTH_LOGIN` | 80% | 75% | 25% |
| `COURSE_TODAY` | 10% | 95% | 5% |
| `LECTURE_LIST` | 10% | 95% | 5% |

`AUTH_LOGIN` 寮傚父缁嗗垎锛?
| 寮傚父 | 姣斾緥 |
|---|---:|
| `SIGNATURE_MISMATCH` | 12% |
| `TOKEN_EXPIRED` | 6% |
| `TIMESTAMP_EXPIRED` | 4% |
| `NONCE_REPLAY` | 3% |

### 6.4 `VENUE_RESERVE_CONFLICT`

鍦哄湴寮€鏀鹃绾︼紝閲嶅鎻愪氦鍜屽啿绐佸鍔犮€?
| API | 娴侀噺鏉冮噸 | NORMAL | 寮傚父 |
|---|---:|---:|---:|
| `AUTH_LOGIN` | 20% | 95% | 5% |
| `VENUE_RESERVE` | 65% | 70% | 30% |
| `CAMPUS_NOTICE` | 15% | 98% | 2% |

`VENUE_RESERVE` 寮傚父缁嗗垎锛?
| 寮傚父 | 姣斾緥 |
|---|---:|
| `RESERVATION_CONFLICT` | 15% |
| `DUPLICATE_REQUEST` | 8% |
| `RATE_LIMITED` | 5% |
| `SLOW_RESPONSE` | 2% |

### 6.5 `LIBRARY_DEPENDENCY_OUTAGE`

鍥句功棣嗕笅娓镐緷璧栧紓甯搞€?
| API | 娴侀噺鏉冮噸 | NORMAL | 寮傚父 |
|---|---:|---:|---:|
| `LIBRARY_BORROW` | 75% | 65% | 35% |
| `AUTH_LOGIN` | 15% | 95% | 5% |
| `CAMPUS_NOTICE` | 10% | 98% | 2% |

`LIBRARY_BORROW` 寮傚父缁嗗垎锛?
| 寮傚父 | 姣斾緥 |
|---|---:|
| `DOWNSTREAM_TIMEOUT` | 18% |
| `DEPENDENCY_UNAVAILABLE` | 12% |
| `SERVICE_ERROR` | 5% |

---

## 7. 鍚庣画瀹炵幇椤哄簭

鎺ㄨ崘鍒嗘瀹炵幇锛?
```text
Step 1锛歛pihub-server 瀹炵幇 Gateway Invoke锛屽畬鎴愬崟娆″閮?API 浠ｇ悊璋冪敤鍜?gateway_log 璁板綍銆?Step 2锛歛pihub-mock-provider 瀹炵幇 Scenario Runner锛屽绾跨▼鎸夋瘮渚嬭皟鐢?Gateway Invoke銆?Step 3锛歛pihub-server 瀹炵幇 Stats Aggregator锛屼粠 gateway_log 鑱氬悎 api_call_stat_hourly銆?Step 4锛歛pihub-server 瀹炵幇 Alert Evaluator锛屾牴鎹粺璁″拰鏃ュ織鐢熸垚 alert_event銆?Step 5锛欰gent Run 璇婃柇鏂扮敓鎴愮殑鏃ュ織銆佺粺璁″拰鍛婅銆?```

涓嬩竴姝ヤ紭鍏堝仛 Step 1锛屼笉瑕佺洿鎺ヨ烦鍒板畬鏁?Scenario Runner銆?
