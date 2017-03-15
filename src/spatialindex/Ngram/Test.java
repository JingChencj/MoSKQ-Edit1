package spatialindex.Ngram;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class Test {
	public static void main(String[] args) throws IOException {
		//---------------------------------------------------è¯»å???»¶??????-----------------------------------------
		FileReader fr = new FileReader("E:/å®??å®¤è???j/Venues/NYC/NYC-Venues.txt");
		BufferedReader br = new BufferedReader(fr);
		String s;
		
		Map<String, String> idString = new HashMap<String, String>();
		Map<String,Vector<String>> invertedList = new HashMap<String,Vector<String>>();
		int edThreshold = 2;
		int K = 3;
		String query = "coffee shop";
	
		while((s=br.readLine())!=null)
		{
			String[] temp = s.split("	");
			String id = temp[0].substring(1, temp[0].length()-1);
			String string = temp[1].substring(1, temp[1].length()-1);
			boolean flag = true;
			for(int j=0;j<string.length();j++){
				if(string.charAt(j)<0 || string.charAt(j)>=128)
					flag = false;
			}
			if(flag){
				idString.put(id, string);
			}
		}
		//--------------------------------------------------------------------------------------------------------
		
	
		for(Map.Entry<String, String> entry: idString.entrySet()){
			String[] temp = entry.getValue().split(" ");
			for(int i = 0;i < temp.length;i ++)
			{	 
				List<String> grams = new ArrayList<String>();
				grams = Ngram.getKGramsList(temp[i], K);
				if(grams!=null){
					for(String str: grams)
					{
						Vector<String> ids = new Vector<String>();
						if(!invertedList.containsKey(str)){
							ids.add(entry.getKey());
							invertedList.put(str, ids);
						} else {
							ids = invertedList.get(str);
							ids.add(entry.getKey());
							invertedList.put(str, ids);
						}
					}
				}
			}
		}
		
		System.out.println("?????»¶??¤§å°?¸ºï¼?" + invertedList.size());
		
		//-------------------------------------------------------------------------------------
		long startTime1=System.currentTimeMillis(); //å¼?å§????
		int j = 0;
		int idnum = 0;
		int num1 = 0;
		for(Map.Entry<String, Vector<String>> entry: invertedList.entrySet()){
		if(query.contains(entry.getKey())){
			j++;
			Vector<String> idSet = new Vector<String>();
			idSet = entry.getValue();
			idnum += idSet.size();
			for(String id: idSet){
				String[] words = idString.get(id).split(" ");
				for(int i = 0;i < words.length;i ++){
					if(EditDistance.ld(words[i], query) <= edThreshold){
						break;
					}
				}
			}
		}
	}
		System.out.println("?°æ?j???¼ä¸ºï¼?" + j);
		System.out.println("?°æ?idnum???¼ä¸ºï¼?" + idnum);
		long endTime1=System.currentTimeMillis();
		System.out.println(num1+"?©ç?N-gram+InvertedList????è¦???¶é?ä¸?"+(endTime1-startTime1)+"ms");
		//----------------------------------------------------------------------------------------------------------
		
		
		//-----------------------------------------------------------------------------------------------------------
		int num2=0;
		long startTime2=System.currentTimeMillis();
		for(Map.Entry<String,String> entry: idString.entrySet()){
			String[] strings = entry.getValue().split(" ");
			for(int i = 0;i < strings.length;i ++){
				if(EditDistance.ld(strings[i],query) <= edThreshold){
					num2++;
					break;
				}
			}
		}
		long endTime2=System.currentTimeMillis();
		System.out.println(num2+"?©ç?çº¿æ?§æ??????è¦???¶é?ä¸?"+(endTime2-startTime2)+"ms");
		//-------------------------------------------------------------------------------------------------------------
	}
}
