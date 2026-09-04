#!/bin/bash
# Prueft die Rhythmuserkennung gegen nachgebildete Herzrhythmen.
# Laeuft auf dem Mac, ohne Uhr - reine Rechenlogik.
set -e
cd "$(dirname "$0")"
W=$(mktemp -d)
mkdir -p "$W/de/doncalvin/herold"
cp ../src/de/doncalvin/herold/RhythmusAnalyse.java "$W/de/doncalvin/herold/"
cp Verteilung.java RhythmusEinzelfaelle.java "$W/"
cd "$W"
javac -nowarn -d . de/doncalvin/herold/RhythmusAnalyse.java Verteilung.java RhythmusEinzelfaelle.java
echo "=== Einzelfaelle ==="
java -cp . RhythmusEinzelfaelle
echo
echo "=== 400 Durchlaeufe je Rhythmusart ==="
java -cp . Verteilung
rm -rf "$W"
