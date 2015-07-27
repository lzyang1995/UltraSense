package jakobkarolus.de.pulseradar.features.gestures;

import java.util.List;
import java.util.ListIterator;
import java.util.Vector;

import jakobkarolus.de.pulseradar.features.Feature;

/**
 * GestureExtractor recognizing upwards Gestures
 * <br><br>
 * Created by Jakob on 08.07.2015.
 */
public class UpGE implements GestureExtractor{

    private double featureLengthMinThr = Double.MAX_VALUE;
    private double featureLengthMaxThr = Double.MIN_VALUE;
    private double featureWeightMinThr = Double.MAX_VALUE;
    private double featureWeightMaxThr = Double.MIN_VALUE;

    @Override
    public List<Gesture> detectGesture(List<Feature> features) {
        List<Gesture> gestures = new Vector<>();
        ListIterator<Feature> iter = features.listIterator();
        while(iter.hasNext()){
            Feature f = iter.next();
            if(f.getLength() >= 1.5 && f.getLength() <= 3.5) {
                if (f.getWeight() <= -60.0) {
                    gestures.add(Gesture.UP);
                    iter.remove();
                }
            }
        }
        return gestures;
    }

    @Override
    public boolean calibrate(List<Feature> features) {
        //do a sanity check
        if(features.size() != 1)
            return false;

        Feature f = features.get(0);
        if(f.getWeight() >= 0.0)
            return false;

        if(f.getWeight() <= featureWeightMinThr)
            featureWeightMinThr = f.getWeight();

        if(f.getWeight() >= featureWeightMaxThr)
            featureWeightMaxThr = f.getWeight();

        if(f.getLength() <= featureLengthMinThr)
            featureLengthMinThr = f.getWeight();

        if(f.getLength() <= featureLengthMaxThr)
            featureLengthMaxThr = f.getWeight();

        return true;
    }
}
