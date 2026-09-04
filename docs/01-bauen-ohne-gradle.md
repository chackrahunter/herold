# 01 — Bauen ohne Gradle

Alle drei Apps werden ohne Gradle und ohne Android Studio gebaut. Jedes Projekt
hat ein `build.sh`, das die vier Werkzeuge des Android-SDK direkt aufruft.

## Warum überhaupt ohne Gradle?

Gradle zieht für ein paar hundert Zeilen Java ein halbes Gigabyte Abhängigkeiten,
einen Daemon und einen langen Kaltstart nach sich. Auf einem Rechner mit wenig
Speicher ist das spürbar. Der direkte Weg baut dieselbe APK in **10–15 Sekunden**,
ist vollständig nachvollziehbar und hat keine versteckte Konfiguration:
Was im `build.sh` steht, passiert — mehr nicht.

Der Preis: Es gibt **keinen Abhängigkeitsauflöser**. Jede Bibliothek muss von
Hand in `libs/` liegen. Die Liste dort *ist* die Wahrheit.

## Die Kette

```
res/          --aapt2 compile-->  res.zip
res.zip       --aapt2 link----->  base.apk + R.java
src/ + R.java --javac---------->  *.class
*.class+libs  --d8------------->  classes.dex
base.apk+dex  --zip+zipalign-->   ausgerichtete APK
              --apksigner----->   fertige, signierte APK
```

Im Klartext (gekürzt aus `herold/build.sh`):

```bash
aapt2 compile --dir res -o build/res.zip
aapt2 link -o build/base.apk -I "$AJAR" --manifest AndroidManifest.xml \
     --java build/gen --min-sdk-version 30 --target-sdk-version 33 build/res.zip
javac -source 17 -target 17 -classpath "$CP" -d build/classes @build/sources.txt
jar cf build/classes.jar -C build/classes .
d8 --min-api 30 --lib "$AJAR" --output build/ build/classes.jar "${LIBS[@]}"
cp build/base.apk build/app-unsigned.apk
(cd build && zip -q app-unsigned.apk classes*.dex)
zipalign -f 4 build/app-unsigned.apk build/app-aligned.apk
apksigner sign --ks "$KS" --out build/app.apk build/app-aligned.apk
```

## Stolpersteine, die dich sonst Stunden kosten

**Bibliotheken müssen in `javac` *und* in `d8`.**
Im Klassenpfad allein reicht nicht — dann kompiliert es sauber und stürzt zur
Laufzeit mit `NoClassDefFoundError` ab. Darum sammeln die Skripte `libs/*.jar`
einmal ein und geben sie an beide weiter.

**Mehrere DEX-Dateien.**
Sobald Bibliotheken dazukommen (Markt: Kotlin, OkHttp, Protobuf), erzeugt `d8`
`classes.dex`, `classes2.dex`, … Das Zippen muss `classes*.dex` einpacken, nicht
nur `classes.dex`. Sonst fehlt zur Laufzeit die Hälfte.

**Den Schlüssel nur einmal erzeugen.**
`build.sh` legt den Keystore an, falls er fehlt — und **nur** dann. Wird bei
jedem Bauen ein neuer erzeugt, ändert sich die Signatur und die Uhr verweigert
das Update mit `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Der Keystore gehört
deshalb **nicht** ins Repo, aber sehr wohl in dein lokales Backup.

**Schlüsselwörter beim Keystore:** `keytool` verlangt mindestens 6 Zeichen
Passwort. Kürzere brechen den Build mit einer wenig hilfreichen Meldung ab.

**Fehler nicht wegfiltern.**
Beim Bauen den Ausgang immer daran festmachen, ob die APK existiert — nicht am
Text der Ausgabe:

```bash
rm -f build/app.apk
./build.sh > /tmp/build.log 2>&1
[ -s build/app.apk ] || grep -iE 'error|fehler' /tmp/build.log
```

**`d8` hat gelegentlich Aussetzer.**
Ein `EOFException` auf einer gültigen JAR ist meist ein Schluckauf — einfach
noch einmal bauen, bevor du die Bibliothek verdächtigst.

## Ziel-SDK bewusst wählen

- **Herold/Markt:** `--target-sdk-version 33`
- **Zifferblatt:** `--target-sdk-version 29` — klassische Ziffernblätter
  (`CanvasWatchFaceService`) werden nur unterhalb eines bestimmten Ziel-SDK als
  „Legacy" behandelt und erscheinen sonst nicht in der Auswahl.
