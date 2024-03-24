package ai.asktheexpert.virtualassistant.models;

import java.util.Random;

public enum Moods {
    Confidence, Sarcastic, Happiness, Sadness, Anger, Fear, Surprise, Disgust, Enthusiasm, Anticipation, Regret,  Hope, Pride, Empathy, Gratitude, Love, Guilt, Curiosity, Clarity, Amazement, Disappointment;

    public static Moods getRandomMood() {
        return getRandomMood(Moods.values());
    }

    public static Moods getRandomMood(Moods[] moods) {
        Random random = new Random();
        return moods[random.nextInt(moods.length)];
    }
}
