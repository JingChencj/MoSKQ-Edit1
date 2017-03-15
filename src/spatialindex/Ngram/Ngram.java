package spatialindex.Ngram;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * K-gram 
 */

public class Ngram {
	public static final int ED_THRESHOLD = 2;
	public static final int DEFAULT_N = 3;
	/**
	 * 停用�?
	 */
	public static Set<String> stopWords = new HashSet<String>(10);
	static {
		stopWords.add("&");
		stopWords.add(" ");
		stopWords.add("\\.");
		stopWords.add("'");
//		// 移除标点符号
		stopWords.add("【|】|\\(|\\)|（|）|\\+|-|\\*|/");
	}
	
	/**
	 * 删除停用�?
	 * 
	 * @param word
	 * @return
	 */
	public static String removeStopWords(String word) {
		if (word == null) {
			return null;
		}
		for (String stopWord : stopWords) {
			word = word.replaceAll(stopWord, "");
		}
		return word;
	}

	/**
	 * 获得字符串的K-gram字符�?
	 * 
	 * @param str
	 * @param k
	 * @return
	 */
	public static List<String> getKGramsList(String str, int k)
	{
		if (str == null || str.trim().isEmpty() || k < 1)//trim方法返回去除首尾空白字符的字符串
		{
			return null;
		}
		str = removeStopWords(str);//去除停用�?
		int length = str.length();//取得字符串的长度
		if(k > length)
			return null;
		List<String> grams = new ArrayList<String>(length);
		for (int i = 0; i < length - k + 1; i++) 
		{
            //截取原词()
            grams.add(str.substring(i, i + k));
    }
		return grams;
	}
	
	/**
	 * 获得不重复的K-gram字符�?
	 * 
	 * @param str
	 * @param k
	 * @return
	 */
	public static Set<String> getKGramsSet(String str, int k) 
	{
		if (str == null || str.isEmpty()) {
			return null;
		}
		return new HashSet<String>(getKGramsList(str, k));
	}
	
	/**
	 * 获得若干字符串的公共K-Gram字符�?
	 * 
	 * @param k
	 * @param str
	 * @return
	 */
	public static Set<String> getNKGramsSet(int k, String...str) 
	{
		if (str == null) {
			return null;
		}
		Set<String> grams = getKGramsSet(str[0], k);
		for (int index = 1 ; index < str.length ; index ++) {
			grams.addAll(getNKGramsSet(k, str[index]));
		}
		return grams;
	}
	
	/**
	 * 获得相交的K-grams的个�?
	 * 
	 * @param set1
	 * @param set2
	 * @return
	 */
	public static int getKGramsSize(Set<String> set1, Set<String> set2) {
		if (set1 == null || set2 == null) {
			return 0;
		}
		// 不可改变原集�?
		Set<String> set = new HashSet<String>(set1);
		// 查找相交的K-grams个数
		set.retainAll(set2);
		return set.size();
	}
	
	public static void main(String[] args) {
		System.out.println(Ngram.getKGramsList("knowledge", 2));
	}
}
