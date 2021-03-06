/*******************************************************************************
 * Copyright (c) 2011 Dipanjan Das 
 * Language Technologies Institute, 
 * Carnegie Mellon University, 
 * All Rights Reserved.
 * 
 * FastFrameIdentifier.java is part of SEMAFOR 2.0.
 * 
 * SEMAFOR 2.0 is free software: you can redistribute it and/or modify  it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * SEMAFOR 2.0 is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. 
 * 
 * You should have received a copy of the GNU General Public License along
 * with SEMAFOR 2.0.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package edu.cmu.cs.lti.ark.fn.identification;


import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectDoubleHashMap;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import edu.cmu.cs.lti.ark.util.ds.map.IntCounter;
import edu.cmu.cs.lti.ark.util.nlp.parse.DependencyParse;
import edu.cmu.cs.lti.ark.util.optimization.LogFormula;

/**
 * @author dipanjan
 * This class is for finding the best frame for a span of tokens, given 
 * the annotation for a sentence. It is just a single node implementation. 
 * Moreover, it looks only at a small set of frames for seen words
 */
public class FastFrameIdentifier extends LRIdentificationModelSingleNode
{
	private THashMap<String, THashSet<String>> mHvCorrespondenceMap;
	private Map<String, Set<String>> mRelatedWordsForWord;
	private THashMap<String,THashSet<String>> clusterMap;
	private Map<String, Map<String, Set<String>>> mRevisedRelationsMap;
	private Map<String, String> mHVLemmas;
	private int K;
	
	public FastFrameIdentifier(TObjectDoubleHashMap<String> paramList, 
									  String reg, 
									  double l, 
									  THashMap<String, THashSet<String>> frameMap, 
									  THashMap<String, THashSet<String>> wnRelationCache,
									  THashMap<String,THashSet<String>> hvCorrespondenceMap,
									  Map<String, Set<String>> relatedWordsForWord,
									  Map<String, Map<String, Set<String>>> revisedRelationsMap,
									  Map<String, String> hvLemmas)
	{
		super(paramList,reg,l,null,frameMap);
		initializeParameterIndexes();
		this.mParamList=paramList;
		mReg=reg;
		mLambda=l;
		mFrameMap=frameMap;
		totalNumberOfParams=paramList.size();
		initializeParameters();
		mLookupChart = new TIntObjectHashMap<LogFormula>();
		mHvCorrespondenceMap = hvCorrespondenceMap;
		mRelatedWordsForWord = relatedWordsForWord;
		mRevisedRelationsMap = revisedRelationsMap;
		mHVLemmas = hvLemmas;
	}
	
	private double getNumeratorValue(String frameName, int[] intTokNums, String[][] data)
	{
		m_current = 0;
		m_llcurrent = 0;		
		double idVal = getValueForFrame(frameName, intTokNums, data);
		return idVal;
	}	
	
	private double getValueForFrame(String frame, int[] intTokNums, String[][] data)	
	{
		THashSet<String> hiddenUnits = mFrameMap.get(frame);
		double result = 0.0;
		DependencyParse parse = DependencyParse.processFN(data, 0.0);
		for (String unit : hiddenUnits)
		{
			IntCounter<String> valMap = null;
			FeatureExtractor featex = new FeatureExtractor();
			if(clusterMap==null) {
				valMap =  
					featex.extractFeaturesLessMemory(frame, 
							intTokNums, 
							unit, 
							data,
							"test", 
							mRelatedWordsForWord, 
							mRevisedRelationsMap,
							mHVLemmas,
							parse);
			} else { // not supported
				valMap =  
					featex.extractFeaturesWithClusters(frame, intTokNums, unit, data, mWNR, "test", null, null,parse,clusterMap,K);
			}
			Set<String> features = valMap.keySet();
			double featSum = 0.0;
			for (String feat : features)
			{
				double val = valMap.getT(feat);
				int ind = localA.get(feat);
				double paramVal = V[ind].exponentiate();
				double prod = val*paramVal;
				featSum+=prod;
			}
			double expVal = Math.exp(featSum);
			result+=expVal;
		}
		return result;
	}
	
	public Set<String> checkPresenceOfTokensInMap(int[] intTokNums, String[][] data)
	{
		String lemmatizedTokens = "";
		for (int i = 0; i < intTokNums.length; i ++)
		{
			lemmatizedTokens+=data[5][intTokNums[i]]+" ";
		}
		lemmatizedTokens=lemmatizedTokens.trim();
		return mHvCorrespondenceMap.get(lemmatizedTokens);
	}
	
	public String getBestFrame(String frameLine, String[][] data, SmoothedGraph sg)
	{
		String result = null;
		double maxVal = -Double.MIN_VALUE;
		String[] toks = frameLine.split("\t");
		String[] tokNums = toks[1].split("_");
		int[] intTokNums = new int[tokNums.length];
		for(int j = 0; j < tokNums.length; j ++) {
			intTokNums[j] = new Integer(tokNums[j]);
		}
		Arrays.sort(intTokNums);

		String finetoken = null;
		String coarsetoken = null;
		Set<String> set = checkPresenceOfTokensInMap(intTokNums,data);
		if (set == null) {
			if (intTokNums.length > 1) {
				coarsetoken = "";
				for (int j = 0; j < intTokNums.length; j++) {
					coarsetoken += data[0][intTokNums[j]].toLowerCase() + " ";
				}
				coarsetoken = coarsetoken.trim();
				coarsetoken = 
					ScanAdverbsAndAdjectives.getCanonicalForm(coarsetoken);
				if (sg.getCoarseMap().containsKey(coarsetoken)) {
					set = sg.getCoarseMap().get(coarsetoken);
				} else {
					set = null;
				}
				
			} else {
				String lemma = data[5][intTokNums[0]];
				String pos = data[1][intTokNums[0]];
				if (pos.startsWith("N")) {
					pos = "n";
				} else if (pos.startsWith("V")) {
					pos = "v";
				} else if (pos.startsWith("J")) {
					pos = "a";
				} else if (pos.startsWith("RB")) {
					pos = "adv";
				} else if (pos.startsWith("I") || pos.startsWith("TO")) {
					pos = "prep";
				} else {
					pos = null;
				}
				if (pos != null) {
					finetoken = 
						ScanAdverbsAndAdjectives.getCanonicalForm(lemma + "." + pos);
					coarsetoken = 
						ScanAdverbsAndAdjectives.getCanonicalForm(lemma);
					if (sg.getFineMap().containsKey(finetoken)) {
						set = sg.getFineMap().get(finetoken);
					} else if (sg.getCoarseMap().containsKey(coarsetoken)){
						set = sg.getCoarseMap().get(coarsetoken);
					} else {
						set = null;
					}
				} else {
					coarsetoken = 
						ScanAdverbsAndAdjectives.getCanonicalForm(lemma);
					if (sg.getCoarseMap().containsKey(coarsetoken)){
						set = sg.getCoarseMap().get(coarsetoken);
					} else {
						set = null;
					}
				}
			}
		}
		if(set==null)
		{
			set = mFrameMap.keySet();
		}
		for(String frame: set)
		{
			double val =  getNumeratorValue(frame, intTokNums, data);
			if(val>maxVal)
			{
				maxVal = val;
				result=""+frame;
			}
			//System.out.println("Considered "+frame+" for frameLine:"+frameLine);
		}
		return result;
	}

	public String getBestFrame(String frameLine, String[][] data)
	{
		String result = null;
		double maxVal = -Double.MIN_VALUE;
		String[] toks = frameLine.split("\t");
		String[] tokNums = toks[1].split("_");
		int[] intTokNums = new int[tokNums.length];
		for(int j = 0; j < tokNums.length; j ++)
			intTokNums[j] = new Integer(tokNums[j]);
		Arrays.sort(intTokNums);

		Set<String> set = checkPresenceOfTokensInMap(intTokNums,data);
		if(set==null)
		{
			set = mFrameMap.keySet();
			System.out.println("Notfound:\t"+frameLine);
		}
		for(String frame: set)
		{
			double val =  getNumeratorValue(frame, intTokNums, data);
			if(val>maxVal)
			{
				maxVal = val;
				result=""+frame;
			}
			//System.out.println("Considered "+frame+" for frameLine:"+frameLine);
		}
		return result;
	}
}

