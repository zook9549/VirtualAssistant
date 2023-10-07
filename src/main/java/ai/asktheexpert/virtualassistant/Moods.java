package ai.asktheexpert.virtualassistant;

import java.util.Random;

public enum Moods {
    Sarcastic, Happiness, Sadness, Anger, Fear, Surprise, Disgust, Anxiety, Contentment, Enthusiasm, Apathy, Jealousy, Anticipation, Impatience, Regret, Relief, Hope, Pride, Shame, Confidence, Insecurity, Loneliness, Empathy, Sympathy, Nostalgia, Gratitude, Love, Hate, Guilt, Indignation, Embarrassment, Humility, Resentment, Curiosity, Confusion, Clarity, Disbelief, Amazement, Ambivalence, Desire, Disappointment;

    public static Moods getRandomMood() {
        return getRandomMood(Moods.values());
    }

    public static Moods getRandomMood(Moods[] moods) {
        Random random = new Random();
        return moods[random.nextInt(moods.length)];
    }
}
