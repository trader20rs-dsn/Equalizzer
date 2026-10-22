# Equalizador e Volume+ (Android)

App Android nativo (Java, sem bibliotecas externas) com:

- **Equalizador** com as bandas do próprio aparelho + presets
- **Amplificador de volume** de até +15 dB (LoudnessEnhancer)
- **Reforço de graves** e **Surround** (quando o aparelho suporta)
- **Serviço em segundo plano** que aplica os efeitos aos players de música
- **Player de teste** embutido, para ouvir os efeitos em qualquer aparelho

Requisitos: Android 5.0 (API 21) ou superior.

## Como gerar o APK

### Opção 1 — GitHub Actions (sem instalar nada)
1. Crie um repositório no GitHub e envie esta pasta inteira (inclusive `.github`).
2. Abra a aba **Actions**; o workflow "Compilar APK" roda sozinho.
3. Ao terminar, baixe o artefato **EqualizadorVolume-apk** (contém `app-debug.apk`).

### Opção 2 — Android Studio
1. **File > Open** e escolha esta pasta.
2. Aguarde a sincronização do Gradle.
3. **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. O arquivo fica em `app/build/outputs/apk/debug/app-debug.apk`.

### Opção 3 — Linha de comando
Com JDK 17, Android SDK (plataforma 34) e Gradle 8.9: `gradle assembleDebug`.

## Instalação
Copie o `app-debug.apk` para o celular, abra o arquivo e permita
"instalar apps de fontes desconhecidas" quando o Android pedir.

## Como usar
1. Abra o app e ative **Efeitos ativados**.
2. Toque em **Iniciar serviço em segundo plano** (aceite a notificação).
3. Abra seu player de música: se ele seguir o padrão de efeitos de áudio do
   Android, a sessão aparece em "Sessões" e o equalizador passa a valer.
4. Para testar sem depender do player, use **Escolher música** no "Player de teste".

## Limitações (importante)
- O Android moderno **não permite um equalizador global garantido** para todos os apps.
  O app tenta a mixagem global (sessão 0), que só funciona em alguns aparelhos, e também
  se anexa aos players que anunciam sua sessão de áudio. Apps que não anunciam
  (muitos de vídeo/streaming) podem não ser afetados.
- Ganho alto distorce o som e pode danificar alto-falantes e a audição.
  Comece com valores baixos.
- O código não foi testado em um aparelho real; ajustes podem ser necessários.
