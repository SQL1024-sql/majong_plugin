# 日本麻將 (立直麻將) — Minecraft Paper 外掛

在 Minecraft 伺服器裡打一場完整的日本麻將：立直、副露、振聽、符計算、寶牌與役滿全都有，
人不夠時由電腦補位，一個人也能開一桌。

- **對應版本**：Paper **26.1.2**（`api-version: 26.1.2`）
- **需要 Java 25**（Paper 26.1.2 的執行環境）

---

## 功能

### 完整的立直麻將規則

| 分類 | 內容 |
| --- | --- |
| 基本流程 | 配牌 13 張、摸打、東風戰／半莊戰／一莊戰、本場、立直棒、連莊 |
| 鳴牌 | 吃、碰、明槓、暗槓、加槓（含搶槓、嶺上開花） |
| 立直 | 立直、兩立直、一發、立直後只能摸切、立直中的暗槓限制、裏寶牌 |
| 振聽 | 捨牌振聽、同巡振聽、立直振聽 |
| 流局 | 荒牌流局（聽牌／不聽罰符 3000）、九種九牌、四風連打、四家立直、四槓散了、三家和 |
| 寶牌 | 表寶牌、槓寶牌、裏寶牌、赤寶牌（張數可設定） |
| 計分 | 符計算（含平和自摸 20 符、七對子 25 符、副露平和形 30 符）、滿貫～三倍滿、累計役滿、切上滿貫（可選） |

### 役種

**1 飜**：立直、一發、門前清自摸和、平和、斷么九、一盃口、役牌（白/發/中/場風/自風）、海底摸月、河底撈魚、嶺上開花、搶槓

**2 飜**：兩立直、三色同順、一氣通貫、混全帶么九、七對子、對對和、三暗刻、三色同刻、三槓子、混老頭、小三元

**3 飜以上**：混一色、純全帶么九、二盃口、清一色

**役滿**：國士無雙（十三面為雙倍）、四暗刻（單騎為雙倍）、大三元、小四喜、大四喜（雙倍）、
字一色、清老頭、綠一色、九蓮寶燈（純正為雙倍）、四槓子、天和、地和

副露後飜數會自動降級（例：混一色門前 3 飜／副露 2 飜），門前限定役（平和、一盃口、七對子…）副露後不成立。
和牌時會自動列舉所有拆解方式，取**點數最高**的那一種計分。

### 電腦對手

座位不夠時自動由電腦補齊。電腦會計算向聽數與進張數決定打牌、聽牌就立直、能和就和，
碰／吃只在役牌或明顯有利時才會叫。玩家離線時也會改由電腦代打，回來後自動接手。

---

## 安裝

1. 用 [Releases](https://github.com/sql1024-sql/majong_plugin/releases) 或自行編譯（見下方）取得 `RiichiMahjong-1.0.0.jar`
2. 丟進伺服器的 `plugins/` 資料夾
3. 重啟伺服器，會自動產生 `plugins/RiichiMahjong/config.yml`

---

## 怎麼玩

```
/mj create            # 開一張牌桌（/mj create east 打東風戰）
/mj join <id>         # 其他人加入
/mj start             # 開始，空位由電腦補齊
```

開始後會自動打開牌桌畫面（箱子介面）：

```
 0- 8   局況、寶牌指示牌、四家點數、自己的情報（聽牌／振聽／牌河）、副露
 9-17   上家的牌河        18-26  對家的牌河        27-35  下家的牌河
36-49   自己的手牌  ←  點一下就是打出這張
50-53   自摸／榮和、立直、槓、九種九牌
```

- **打牌**：點手牌，或 `/mj discard 5m`
- **立直**：點「立直」按鈕後再點要打的牌，或 `/mj riichi` 選擇捨牌
- **鳴牌**：別人打出可以鳴的牌時，聊天欄會出現 `[碰] [吃 三索四索] [榮] [跳過]` 直接點就好
- **看牌況**：`/mj info`，或 `/mj open` 重新打開畫面

牌的寫法：`1m`＝一萬、`5p`＝五筒、`9s`＝九索、`1z`～`7z`＝東南西北白發中、`0m`＝赤五萬。

### 指令

| 指令 | 說明 |
| --- | --- |
| `/mj create [east\|hanchan]` | 開新牌桌 |
| `/mj join <id>` / `/mj leave` | 加入／離開牌桌 |
| `/mj start` | 開始對局（限開桌者） |
| `/mj list` | 列出所有牌桌 |
| `/mj open` | 打開牌桌畫面 |
| `/mj info` | 顯示目前牌況 |
| `/mj discard <牌>` | 打出一張牌 |
| `/mj riichi [牌]` | 立直 |
| `/mj tsumo` / `/mj ron` | 自摸／榮和 |
| `/mj pon` / `/mj chi <牌> <牌>` / `/mj kan [牌]` | 碰／吃／槓 |
| `/mj call <編號>` / `/mj pass` | 選擇鳴牌選項／跳過 |
| `/mj kyuushu` | 宣告九種九牌 |
| `/mj reload` | 重新載入設定（需 `mahjong.admin`） |

別名：`/mahjong`、`/riichi`。

### 權限

| 權限 | 預設 | 說明 |
| --- | --- | --- |
| `mahjong.play` | 所有人 | 開桌與遊玩 |
| `mahjong.admin` | OP | `/mj reload` |

---

## 設定

`plugins/RiichiMahjong/config.yml`：

```yaml
rules:
  aka-dora: 3            # 赤寶牌張數 (0/3/4)
  kuitan: true           # 喰斷
  kazoe-yakuman: true    # 累計役滿
  double-yakuman: true   # 雙倍役滿
  kiriage-mangan: false  # 切上滿貫
  starting-points: 25000
  rounds: 2              # 1=東風戰 2=半莊 4=一莊
  abortive-draws: true   # 途中流局
  multiple-ron: true     # 多家和
  tenpai-renchan: true   # 莊家聽牌連莊
  end-on-bankrupt: true  # 被擊飛就結束
  turn-seconds: 20       # 思考時間

bots:
  delay-millis: 1000     # 電腦思考的等待時間

game:
  round-break-millis: 8000  # 每局之間的休息時間
  log-discards: true        # 是否把每張捨牌顯示在聊天欄
```

---

## 編譯

需要 JDK 25 與 Maven：

```bash
mvn package          # 產出 target/RiichiMahjong-1.0.0.jar
mvn test             # 只跑測試
```

測試包含牌型拆解、向聽數、役種與符數的單元測試，以及**讓四個電腦玩家打完 20 場半莊**、
逐局驗證點數守恆的模擬測試。

## 專案結構

```
core/    純 Java 的麻將規則引擎（不依賴 Bukkit，可單獨拿去用）
         Tile Wall Meld HandParser ShantenCalculator YakuEvaluator ScoreCalculator …
game/    牌桌流程狀態機：MahjongTable + TableListener
ai/      SimpleBot：向聽數 / 進張數導向的電腦對手
bukkit/  Paper 介接：指令、箱子介面、聊天按鈕、排程與逾時處理
```

`core` 與 `game` 完全不碰 Minecraft API，所以規則本身可以直接寫單元測試，
也方便日後接到其他介面（Discord bot、網頁…）。

## 目前沒有實作的規則

- 流局滿貫（流し満貫）
- 包牌／責任支付（大三元・大四喜的包則）
- 途中流局的一部分地方規則（例如四開槓的細節差異）
- 順位馬點（ウマ／オカ）與連對統計

歡迎 issue 或 PR。
