# DOIDO DO TIRO

Jogo de tiro em primeira pessoa (FPS) para **Android**, escrito em **Kotlin** com
**OpenGL ES 2.0** puro. Nao usa engine, nao usa biblioteca externa e nao carrega
nenhum arquivo de modelo ou imagem: a arena, as texturas, as armas e ate o
boneco da tela inicial sao gerados em codigo.

## Baixar o APK pronto (nao precisa instalar nada)

O GitHub Actions compila o jogo a cada `push` e publica o APK na release
**latest**. Baixe este link **direto no celular**:

**https://github.com/eric2021r/doido-do-tiro-1/releases/download/latest/arena-fps-debug.apk**

Abra o arquivo baixado e autorize "instalar apps de fontes desconhecidas".
O app aparece na gaveta como **Arena FPS**.

> O pacote do build de debug e `com.example.fpsarena.debug`, entao ele convive
> em paz com uma versao de release instalada ao mesmo tempo.

## O que tem no jogo

- **Menu inicial** com o nome do jogo, um operador armado estilo capa do
  Counter-Strike 1.6 (desenhado com Canvas) e os botoes **NOVO JOGO**,
  **COMO JOGAR** e **SAIR**.
- **5 armas**: pistola, fuzil, shotgun, sniper e granada.
- **Animacao de morte**: o inimigo cai de bruços e o corpo desaparece.
- **Granada com fisica**: cai, quica nas paredes e explode em area.
- **Luneta da sniper**: zoom real (FOV fecha) com a mira de luneta na tela.
- Inimigos em ondas, placar, vida, municao por arma e recarga.

## Compilacao automatica (GitHub Actions)

O workflow fica em `.github/workflows/build-apk.yml` e roda:

1. em todo `push` na branch `main`;
2. manualmente, pelo botao **Run workflow** na aba *Actions*.

Ele compila o APK de debug, guarda como artifact (aba *Actions > run > Artifacts*)
e atualiza a release `latest` com o arquivo `arena-fps-debug.apk`.

A keystore de debug fica em cache entre as execucoes, para o Android aceitar
instalar um build novo por cima do antigo. Se voce desinstalar o app, o link
continua o mesmo.

## Requisitos (para compilar na sua maquina)

- **Android Studio** (Ladybug 2024.2 ou mais novo) com o SDK 35 instalado.
- O JDK vem junto com o Android Studio (nao precisa instalar Java a parte).
- Celular/emulador com Android 7.0 (API 24) ou superior.

## Como abrir e rodar

1. Abra o Android Studio.
2. `File > Open...` e escolha a pasta **`jogo novo`** (a que tem o `settings.gradle.kts`).
3. Espere o *Gradle Sync* terminar (na primeira vez ele baixa o Gradle 8.9 e o
   Android Gradle Plugin 8.7.3).
4. Conecte o celular com depuracao USB ligada (ou crie um emulador).
5. Clique em **Run ▶**.

O jogo abre em tela cheia no modo paisagem.

## Gerar o APK pela linha de comando

Na pasta do projeto:

```powershell
# APK de debug (instalavel direto, sem assinatura propria)
.\gradlew.bat assembleDebug

# APK de release
.\gradlew.bat assembleRelease
```

O arquivo sai em:

- Debug: `app\build\outputs\apk\debug\app-debug.apk`
- Release: `app\build\outputs\apk\release\app-release-unsigned.apk`

Para instalar no celular via USB:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

> O APK de release sai **sem assinatura**. Para publicar na Play Store ou
> instalar direto, assine com o seu keystore
> (`Build > Generate Signed Bundle / APK...` no Android Studio).

## Controles (tela sensivel ao toque)

| Controle | O que faz |
|---|---|
| Metade **esquerda** (arrastar) | Joystick de movimento. O joystick aparece onde o dedo tocar. |
| Metade **direita** (arrastar) | Gira a camera (olhar). |
| Botao **ATIRAR** (canto inferior direito) | Dispara a arma equipada. Com a granada selecionada, arremessa. |
| Botao **PULO** | Pula. |
| Botao **RECARGA** | Recarrega o pente da arma atual. |
| Botao **TROCAR** | Passa para a proxima arma (a lista no topo mostra qual esta equipada). |
| Botao **MIRA** | Liga/desliga a luneta. So aparece com a **sniper** equipada. |

Atalhos de teclado/mouse (uteis no emulador): `WASD` ou setas para andar,
`Ctrl`/`Enter` para atirar, `Espaco` para pular, `R` para recarregar,
`Q` para trocar de arma, `F` para a luneta e arrastar o mouse para olhar.

## As armas

| Arma | Dano | Pente | Recarga | Observacoes |
|---|---|---|---|---|
| **PISTOLA** | 30 | 12 | 1,1 s | Tiro a tiro, muito precisa, municao farta. |
| **FUZIL** | 34 | 30 | 1,5 s | Rajada automatica, arma de uso geral. |
| **SHOTGUN** | 16 x 8 balins | 6 | 2,0 s | Devasta de perto; o chumbo abre muito de longe. |
| **SNIPER** | 130 | 5 | 2,4 s | Mata de um tiro; tem luneta com zoom. Mirar deixa voce lento. |
| **GRANADA** | 165 no centro | 4 | - | Arremessada, quica e explode em area (raio de 4,8 m). |

Ao limpar uma onda voce recebe municao de reserva de **todas** as armas e
25 de vida.

## Como se joga

- Cada onda traz mais inimigos (`4 + onda * 2`, ate 20). Eles perseguem e
  atacam de perto.
- Ao morrer o inimigo tomba e o corpo some depois de ~1,7 s.
- Se a vida chegar a zero, aparece a tela de game over, com a opcao de jogar de
  novo ou **VOLTAR AO MENU**.

## Estrutura do projeto

```
app/src/main/java/com/example/fpsarena/
  MainActivity.kt      monta a pilha (OpenGL, HUD, menu)
  GameView.kt          SurfaceView do OpenGL + teclado/mouse
  MenuView.kt          tela inicial: titulo, botoes e o boneco estilo CS 1.6
  GameRenderer.kt      loop de render: arena, inimigos, corpos, granadas,
                       explosoes, arma e camera do menu
  Game.kt              regras: jogador, IA, ondas, granadas, raycast, colisoes
  Weapon.kt            ficha das 5 armas (dano, pente, recarga, cadencia)
  Level.kt             mapa da arena (paredes, pilares, caixotes)
  HudView.kt           vida/municao/armas/luneta + controles de toque
  InputState.kt        ponte de input entre a UI e a thread de render
  ShaderProgram.kt     shader unico (luz direcional + neblina)
  Mesh.kt / MeshFactory.kt     malhas (cubo, quadrado) e VBOs
  TextureFactory.kt    texturas geradas com Canvas (piso, parede, caixote)
```

## Onde mexer para ajustar o jogo

**Armas:** tudo em `Weapon.kt` (bloco `object Weapons`). Cada arma tem dano,
pente, municao de reserva, cadencia, tempo de recarga, dispersao e coice.

**Regras gerais:** constantes no `companion object` de `Game.kt`.

| Constante | Padrao | Efeito |
|---|---|---|
| `WALK_SPEED` | 5.0 | Velocidade do jogador andando |
| `AIM_SPEED` | 2.2 | Velocidade com a luneta ligada |
| `LOOK_SENSITIVITY` | 0.0042 | Sensibilidade do arraste da camera |
| `ENEMY_DAMAGE` | 9 | Dano de cada ataque de inimigo |
| `GRENADE_RADIUS` | 4.8 | Raio da explosao |
| `GRENADE_DAMAGE` | 165 | Dano no centro da explosao |
| `MENU_ORBIT_RADIUS` | 13 | Raio da camera que gira no menu |

Outros pontos uteis:

- Quantidade de inimigos por onda: `spawnWave()` (`4 + wave * 2`, maximo 20).
- Velocidade dos inimigos: `enemy.speed` em `spawnWave()`.
- Tempo que o corpo leva para cair e sumir: `CORPSE_DURATION` em `Game.kt` e as
  propriedades `fallFactor` / `corpseAlpha` da classe `Enemy`.
- Mapa: bloco `init` de `Level.kt`.
- Desenho do boneco do menu: `drawSoldier()` em `MenuView.kt` (coordenadas
  locais de 100 x 200, escala aplicada na hora de desenhar).

## Estado atual

O projeto compila no GitHub Actions (o APK publicado sai de um build
automatico). Nesta maquina nao ha JDK nem Android SDK instalados, entao a
compilacao local nao foi feita aqui; ao abrir no Android Studio o primeiro sync
resolve isso. Se aparecer algum erro de build, me mande a mensagem que eu ajusto.
