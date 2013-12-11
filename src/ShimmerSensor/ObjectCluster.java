package ShimmerSensor;

import java.util.Collection;
import java.util.Iterator;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class ObjectCluster {
	public Multimap<String, FormatCluster> mPropertyCluster = HashMultimap.create();
	public String mMyName;
	public ObjectCluster(String myName){
		mMyName = myName;
	}
	 /**
     * Takes in a collection of Format Clusters and returns the Format Cluster specified by the string format
     * @param formatCluster
     * @return
     */
   public FormatCluster returnFormatCluster(Collection<FormatCluster> collectionFormatCluster, String format){
    	Iterator<FormatCluster> iFormatCluster=collectionFormatCluster.iterator();
    	FormatCluster formatCluster;
    	FormatCluster returnFormatCluster = null;
    	
    	while(iFormatCluster.hasNext()){
    		formatCluster=(FormatCluster)iFormatCluster.next();
    		if (formatCluster.mFormat==format){
    			returnFormatCluster=formatCluster;
    		}
    	}
		return returnFormatCluster;
    }
	
}
