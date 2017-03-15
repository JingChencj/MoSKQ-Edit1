package Test;

import spatialindex.rtree.RTree;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class test {
	public int te(){
		System.out.println("test1");
		return 1;
	}

	public void te(int a){
		System.out.println("test2");
	}

	//以下两个参数类型顺序不同
	public String te(int a,String s){
		System.out.println("test3");
		return "returntest3";
	}

	public String te(String s,int a){
		System.out.println("test4");
		return "returntest4";
	}

	public static void main(String[] args){
		test o = new test();
		System.out.println(o.te());
		o.te(1);
		System.out.println(o.te(1,"test3"));
		System.out.println(o.te("test4",1));
	}

	private static ArrayList<ArrayList<Integer>> perm(List<Integer> inputList,int k ){
		ArrayList<ArrayList<Integer>> resList = new ArrayList<>();
		
	    ArrayList<Integer> arr = new ArrayList<>();
		permInt(inputList, resList, 0,0,arr,k );//new char[inputList.size()] -> new ArrayList<String>() 
	    return resList;
	}

	private static void permInt(List<Integer> inputList, List<ArrayList<Integer>> resList, int ind, int gnd, ArrayList<Integer> arr,int k) {
	    if(ind == k){
	    	ArrayList<Integer> clone = (ArrayList<Integer>) arr.clone();
			resList.add(clone);
	        return;
	    }
	    for(int i = gnd;i<inputList.size();++i){
	    	arr.add(inputList.get(i));
	    	permInt(inputList,resList,ind+1,i+1,arr,k);
	    	arr.remove(arr.size()-1);
	    }
	}
}	
