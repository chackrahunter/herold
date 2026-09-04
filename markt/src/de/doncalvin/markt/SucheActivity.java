package de.doncalvin.markt;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Ein Feld, die Uhrentastatur, fertig. */
public class SucheActivity extends Activity {
    private EditText feld;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout wurzel = new LinearLayout(this);
        wurzel.setOrientation(LinearLayout.VERTICAL); wurzel.setGravity(Gravity.CENTER);
        wurzel.setBackgroundColor(Stil.GRUND);
        int r = Stil.dp(this, 28);
        wurzel.setPadding(r, 0, r, 0);

        TextView t = Oberflaeche.kopf(this, "Suchen", null);
        wurzel.addView(t);

        feld = new EditText(this);
        feld.setHint("App oder Zifferblatt");
        feld.setHintTextColor(Stil.TEXT_LEISE);
        feld.setTextColor(Stil.TEXT_STARK); feld.setTextSize(15);
        feld.setSingleLine(true);
        feld.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        feld.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        GradientDrawable g = new GradientDrawable(); g.setColor(Stil.FLAECHE_02); g.setCornerRadius(Stil.dp(this, 20));
        g.setStroke(Stil.dp(this, 1), Stil.RAND_DEUTLICH);
        feld.setBackground(g);
        int p = Stil.dp(this, 14);
        feld.setPadding(p, Stil.dp(this, 10), p, Stil.dp(this, 10));
        feld.setOnEditorActionListener((v, id, ev) -> { if (id == EditorInfo.IME_ACTION_SEARCH || ev != null) { los(); return true; } return false; });
        wurzel.addView(feld, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView h = Oberflaeche.fuss(this, "Eingabe mit der Uhrentastatur,\nEnter startet die Suche");
        wurzel.addView(h);
        setContentView(wurzel);

        feld.requestFocus();
        feld.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(feld, InputMethodManager.SHOW_IMPLICIT);
        }, 250);
    }

    private void los() {
        String f = feld.getText().toString().trim();
        if (f.isEmpty()) return;
        startActivity(new Intent(this, ListeActivity.class).putExtra("modus", ListeActivity.SUCHE).putExtra("frage", f));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
