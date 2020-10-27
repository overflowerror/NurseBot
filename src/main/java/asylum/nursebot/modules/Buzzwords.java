package asylum.nursebot.modules;

import java.util.LinkedList;
import java.util.List;

import com.google.inject.Inject;

import asylum.nursebot.loader.AutoModule;
import asylum.nursebot.modules.buzzwords.Buzzword;
import asylum.nursebot.objects.Module;
import asylum.nursebot.objects.ModuleType;
import asylum.nursebot.semantics.SemanticsHandler;
import asylum.nursebot.semantics.WakeWordType;

@AutoModule(load=true)
public class Buzzwords implements Module {
    @Inject
    private SemanticsHandler semanticsHandler;


    private List<Buzzword> buzzwords = new LinkedList<>();
    @Override
    public String getName() {
        return "Buzzwords";
    }

    @Override
    public ModuleType getType() {
        return new ModuleType().set(ModuleType.SEMANTIC_MODULE);
    }

    @Override
    public void init() {
        buzzwords.add(new Buzzword(this, new String[] {
                    "scheiße", "scheiß", "fuck"
            }, new String[]{
                    "Ich dulde keine Kraftausdrücke hier!", "Hey! Achte auf deine Sprache!", "Hey! Es sind Kinder anwesend."
            }, 0.2, 0, WakeWordType.ANYWHERE));
        buzzwords.add(new Buzzword(this, new String[] {
                    "mau", "mau.", "miau", "miau.", "meow", "meow.", "nyan", "nyan."
            }, new String[] {
                    "*streichel*", "Mau", "*flausch*", ":3"
        }, 1, 2000));

        buzzwords.add(new Buzzword(this, new String[] {
                "minecraft"
        }, new String[] {
                "BTW: Es gibt auch einen Minecraft Server von cyber-missile.", "Oh, redest du vom cyber-missile Minecraft Server?", "Ich mag Minecraft. :3"
        }, 0.1, 1000, WakeWordType.ANYWHERE));

        buzzwords.add(new Buzzword(this, new String[] {"Joshua"}, new String[] {"HALLO PROFESSOR FALKEN\n\nSHALL WE PLAY A GAME?"},
                1, 1000, WakeWordType.STANDALONE)
        );

        for (Buzzword buzzword : buzzwords) {
            semanticsHandler.add(buzzword);
        }
    }

    @Override
    public void activate() {

    }

    @Override
    public void deactivate() {

    }

    @Override
    public void shutdown() {

    }
}
