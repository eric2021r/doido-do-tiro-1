# Arena FPS

Jogo de tiro em primeira pessoa (FPS) para **Android**, escrito em **Kotlin** com
**OpenGL ES 2.0** puro. Nao usa engine, nao usa biblioteca externa e nao carrega
nenhum arquivo de modelo ou imagem: a arena, as texturas e a arma sao geradas em
codigo.

## Requisitos

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
| Botao **ATIRAR** (canto inferior direito) | Atira. Segure para tiro automatico. |
| Botao **PULO** | Pula. |
| Botao **RECARGA** | Recarrega o pente (30 balas). |

Atalhos de teclado/mouse (uteis no emulador): `WASD` ou setas para andar,
`Ctrl`/`Enter` para atirar, `Espaco` para pular, `R` para recarregar e arrastar
o mouse para olhar.

## Como se joga

- Cada onda traz mais inimigos. Os inimigos perseguem e atacam de perto.
- Sao necessarios **3 tiros** para derrubar um inimigo (34 de dano por tiro).
- Ao limpar a onda voce ganha pontos, municao de reserva e 25 de vida.
- Se a vida chegar a zero, aparece a tela de game over; toque na tela para
  jogar de novo.

## Estrutura do projeto

```
app/src/main/java/com/example/fpsarena/
  MainActivity.kt      monta a tela (OpenGL embaixo, HUD em cima)
  GameView.kt          SurfaceView do OpenGL + teclado/mouse
  GameRenderer.kt      loop de render: arena, inimigos, arma, tracantes
  Game.kt              regras: jogador, tiro (raycast), IA, ondas, colisoes
  Level.kt             mapa da arena (paredes, pilares, caixotes)
  HudView.kt           vida/municao/placar + controles de toque
  InputState.kt        ponte de input entre a UI e a thread de render
  ShaderProgram.kt     shader unico (luz direcional + neblina)
  Mesh.kt / MeshFactory.kt     malhas (cubo, quadrado) e VBOs
  TextureFactory.kt    texturas geradas com Canvas (piso, parede, caixote)
```

## Onde mexer para ajustar o jogo

Quase tudo que muda o "feeling" esta em `Game.kt`, no bloco `companion object`:

| Constante | Padrao | Efeito |
|---|---|---|
| `WALK_SPEED` | 5.0 | Velocidade do jogador |
| `LOOK_SENSITIVITY` | 0.0042 | Sensibilidade do arrasto da camera |
| `FIRE_INTERVAL` | 0.105 | Intervalo entre tiros (segundos) |
| `RELOAD_DURATION` | 1.5 | Tempo de recarga |
| `MAX_SHOOT_DISTANCE` | 90 | Alcance do tiro |

Outros pontos uteis:

- Dano por tiro do jogador: `enemy.health -= 34f` dentro de `shoot()`.
- Vida dos inimigos: `Enemy.health` (padrao 100).
- Quantidade de inimigos por onda: `spawnWave()` (`4 + wave * 2`, maximo 20).
- Velocidade dos inimigos: `enemy.speed` em `spawnWave()`.
- Dano que o inimigo causa: `ENEMY_DAMAGE` (padrao 9).
- Mapa: bloco `init` de `Level.kt`.

## Estado atual

O projeto foi escrito e conferido, mas **nao foi compilado nesta maquina**
porque nao ha JDK nem Android SDK instalados aqui. Ao abrir no Android Studio o
primeiro sync resolve isso; se aparecer algum erro de build, me mande a mensagem
que eu ajusto.
