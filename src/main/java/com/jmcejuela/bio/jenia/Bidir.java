package com.jmcejuela.bio.jenia;

import static com.jmcejuela.bio.jenia.util.Util.last;
import static com.jmcejuela.bio.jenia.util.Util.newArrayList;
import static com.jmcejuela.bio.jenia.util.Util.tokenize;
import static java.lang.Character.isDigit;
import static java.lang.Character.isUpperCase;
import static java.lang.Math.log;
import static java.lang.Math.max;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jmcejuela.bio.jenia.common.Sentence;
import com.jmcejuela.bio.jenia.common.Token;
import com.jmcejuela.bio.jenia.maxent.ME_Model;
import com.jmcejuela.bio.jenia.maxent.ME_Sample;
import com.jmcejuela.bio.jenia.util.Constructor;
import com.jmcejuela.bio.jenia.util.Tuple2;

/**
 * From bidir.cpp
 */
public class Bidir {

  static final int UPDATE_WINDOW_SIZE = 2;
  // static finalint BEAM_NUM = 10;
  static final int BEAM_NUM = 1;
  static final double BEAM_WINDOW = 0.01;
  // final double BEAM_WINDOW = 0.9;
  static final boolean ONLY_VERTICAL_FEATURES = false;

  private static ME_Sample mesample(
      final ArrayList<Token> vt,
      int i,
      final String pos_left2,
      final String pos_left1,
      final String pos_right1,
      final String pos_right2)
  {
    ME_Sample sample = new ME_Sample();

    String str = vt.get(i).text;

    sample.label = vt.get(i).pos;

    sample.features.add("W0_" + str);
    String prestr = "BOS";
    if (i > 0) prestr = vt.get(i - 1).text;
    // String prestr2 = "BOS2";
    // if (i > 1) prestr2 = normalize(vt[i-2].str);
    String poststr = "EOS";
    if (i < vt.size() - 1) poststr = vt.get(i + 1).text;
    // String poststr2 = "EOS2";
    // if (i < (int)vt.size()-2) poststr2 = normalize(vt[i+2].str);

    if (!ONLY_VERTICAL_FEATURES) {
      sample.features.add("W-1_" + prestr);
      sample.features.add("W+1_" + poststr);

      sample.features.add("W-10_" + prestr + "_" + str);
      sample.features.add("W0+1_" + str + "_" + poststr);
    }

    for (int j = 1; j <= 10; j++) {
      if (str.length() >= j) {
        sample.features.add(
            String.format("suf%d_%s", j, str.substring(str.length() - j)));
      }
      if (str.length() >= j) {
        sample.features.add(
            String.format("pre%d_%s", j, str.substring(0, j)));
      }
    }
    // L
    if (!pos_left1.isEmpty()) {
      sample.features.add("P-1_" + pos_left1);
      sample.features.add("P-1W0_" + pos_left1 + "_" + str);
    }
    // L2
    if (!pos_left2.isEmpty()) {
      sample.features.add("P-2_" + pos_left2);
    }
    // R
    if (!pos_right1.isEmpty()) {
      sample.features.add("P+1_" + pos_right1);
      sample.features.add("P+1W0_" + pos_right1 + "_" + str);
    }
    // R2
    if (!pos_right2.isEmpty()) {
      sample.features.add("P+2_" + pos_right2);
    }
    // LR
    if (!pos_left1.isEmpty() && !pos_right1.isEmpty()) {
      sample.features.add("P-1+1_" + pos_left1 + "_" + pos_right1);
      sample.features.add("P-1W0P+1_" + pos_left1 + "_" + str + "_" + pos_right1);
    }
    // LL
    if (!pos_left1.isEmpty() && !pos_left2.isEmpty()) {
      sample.features.add("P-2-1_" + pos_left2 + "_" + pos_left1);
      // sample.features.add("P-1W0_" + pos_left + "_" + str);
    }
    // RR
    if (!pos_right1.isEmpty() && !pos_right2.isEmpty()) {
      sample.features.add("P+1+2_" + pos_right1 + "_" + pos_right2);
      // sample.features.add("P-1W0_" + pos_left + "_" + str);
    }
    // LLR
    if (!pos_left1.isEmpty() && !pos_left2.isEmpty() && !pos_right1.isEmpty()) {
      sample.features.add("P-2-1+1_" + pos_left2 + "_" + pos_left1 + "_" + pos_right1);
      // sample.features.add("P-1W0_" + pos_left + "_" + str);
    }
    // LRR
    if (!pos_left1.isEmpty() && !pos_right1.isEmpty() && !pos_right2.isEmpty()) {
      sample.features.add("P-1+1+2_" + pos_left1 + "_" + pos_right1 + "_" + pos_right2);
      // sample.features.add("P-1W0_" + pos_left + "_" + str);
    }
    // LLRR
    if (!pos_left2.isEmpty() && !pos_left1.isEmpty() && !pos_right1.isEmpty() && !pos_right2.isEmpty()) {
      sample.features.add("P-2-1+1+2_" + pos_left2 + "_" + pos_left1 + "_" + pos_right1 + "_" + pos_right2);
      // sample.features.add("P-1W0_" + pos_left + "_" + str);
    }

    for (int j = 0; j < str.length(); j++) {
      if (isDigit(str.charAt(j))) {
        sample.features.add("CONTAIN_NUMBER");
        break;
      }
    }
    for (int j = 0; j < str.length(); j++) {
      if (isUpperCase(str.charAt(j))) {
        sample.features.add("CONTAIN_UPPER");
        break;
      }
    }
    for (int j = 0; j < str.length(); j++) {
      if (str.charAt(j) == '-') {
        sample.features.add("CONTAIN_HYPHEN");
        break;
      }
    }

    boolean allupper = true;
    for (int j = 0; j < str.length(); j++) {
      if (!isUpperCase(str.charAt(j))) {
        allupper = false;
        break;
      }
    }
    if (allupper)
      sample.features.add("ALL_UPPER");

    // for (int j = 0; j < vt.size(); j++)
    // cout << vt[j].str << " ";
    // cout << endl;
    // cout << i << endl;
    // for (List<String>::final_iterator j = sample.features.begin(); j != sample.features.end(); j++) {
    // cout << *j << " ";
    // }
    // cout << endl << endl;

    return sample;
  }

  /*****************************
   * //////////////////////////////////////////////////////////////////// //// Toutanova feature
   * //////////////////////////////////////////////////////////////////// static ME_Sample mesample(final
   * ArrayList<Token> &vt, int i, final String & pos_left2, final String & pos_left1, final String & pos_right1, final
   * String & pos_right2) { ME_Sample sample = new ME_Sample();
   *
   * String str = vt.get(i).str;
   *
   * sample.label = vt.get(i).pos;
   *
   * sample.features.add("W0_" + str); String prestr = "BOS"; if (i > 0) prestr = vt[i-1].str; // String prestr2 =
   * "BOS2"; // if (i > 1) prestr2 = normalize(vt[i-2].str); String poststr = "EOS"; if (i < (int)vt.size()-1) poststr =
   * vt[i+1].str; // String poststr2 = "EOS2"; // if (i < (int)vt.size()-2) poststr2 = normalize(vt[i+2].str);
   *
   * if (!ONLY_VERTICAL_FEATURES) { sample.features.add("W-1_" + prestr); sample.features.add("W+1_" + poststr);
   *
   * sample.features.add("W-10_" + prestr + "_" + str); sample.features.add("W0+1_" + str + "_" + poststr); }
   *
   * for (int j = 1; j <= 10; j++) { char buf[1000]; if (str.length() >= j) { sprintf(buf, "suf%d_%s", j,
   * str.substring(str.length() - j)); sample.features.add(buf); } if (str.length() >= j) { sprintf(buf, "pre%d_%s", j,
   * str.substring(0, j)); sample.features.add(buf); } } // L if (!pos_left1.isEmpty()) { sample.features.add("P-1_" +
   * pos_left1); sample.features.add("P-1W0_" + pos_left1 + "_" + str); }
   *
   * // L2 // if (!pos_left2.isEmpty()) { // sample.features.add("P-2_" + pos_left2); // }
   *
   * // R if (!pos_right1.isEmpty()) { sample.features.add("P+1_" + pos_right1); sample.features.add("P+1W0_" +
   * pos_right1 + "_" + str); }
   *
   * // R2 // if (!pos_right2.isEmpty()) { // sample.features.add("P+2_" + pos_right2); // }
   *
   * // LR if (!pos_left1.isEmpty() && !pos_right1.isEmpty()) { sample.features.add("P-1+1_" + pos_left1 + "_" +
   * pos_right1); // sample.features.add("P-1W0P+1_" + pos_left1 + "_" + str + "_" + pos_right1); } // LL if
   * (!pos_left1.isEmpty() && !pos_left2.isEmpty()) { sample.features.add("P-2-1_" + pos_left2 + "_" + pos_left1); //
   * sample.features.add("P-1W0_" + pos_left + "_" + str); } // RR if (!pos_right1.isEmpty() && !pos_right2.isEmpty()) {
   * sample.features.add("P+1+2_" + pos_right1 + "_" + pos_right2); // sample.features.add("P-1W0_" + pos_left + "_" +
   * str); }
   *
   * // LLR // if (!pos_left1.isEmpty() && !pos_left2.isEmpty() && !pos_right1.isEmpty()) { //
   * sample.features.add("P-2-1+1_" + pos_left2 + "_" + pos_left1 + "_" + pos_right1); // //
   * sample.features.add("P-1W0_" + pos_left + "_" + str); // } // LRR // if (!pos_left1.isEmpty() &&
   * !pos_right1.isEmpty() && !pos_right2.isEmpty()) { // sample.features.add("P-1+1+2_" + pos_left1 + "_" + pos_right1
   * + "_" + pos_right2); // // sample.features.add("P-1W0_" + pos_left + "_" + str); // } // LLRR // if
   * (!pos_left2.isEmpty() && !pos_left1.isEmpty() && !pos_right1.isEmpty() && !pos_right2.isEmpty()) { //
   * sample.features.add("P-2-1+1+2_" + pos_left2 + "_" + pos_left1 + "_" + pos_right1 + "_" + pos_right2); // //
   * sample.features.add("P-1W0_" + pos_left + "_" + str); // }
   *
   * boolean contain_number = false; for (int j = 0; j < str.length(); j++) { if (isdigit(str[j])) {
   * sample.features.add("CONTAIN_NUMBER"); contain_number = true; break; } } boolean contain_upper = false; for (int j
   * = 0; j < str.length(); j++) { if (isupper(str[j])) { sample.features.add("CONTAIN_UPPER"); contain_upper = true;
   * break; } } boolean contain_hyphen = false; for (int j = 0; j < str.length(); j++) { if (str[j] == '-') {
   * sample.features.add("CONTAIN_HYPHEN"); contain_hyphen = true; break; } } if (contain_number && contain_upper &&
   * contain_hyphen) { sample.features.add("CONTAIN_NUMBER_UPPER_HYPHEN"); } if (contain_upper) { boolean company =
   * false; for (int j = i + 1; j <= i + 3; j++) { if (j >= vt.size()) continue; if (vt[j].str.equals("Co.")) company =
   * true; if (vt[j].str.equals("Inc.")) company = true; if (vt[j].str.equals("Corp.")) company = true; } if (company)
   * sample.features.add("CRUDE_COMPANY_NAME"); }
   *
   * boolean allupper = true; for (int j = 0; j < str.length(); j++) { if (!isupper(str[j])) { allupper = false; break;
   * } } if (allupper) sample.features.add("ALL_UPPER");
   *
   * // for (int j = 0; j < vt.size(); j++) // cout << vt[j].str << " "; // cout << endl; // cout << i << endl; // for
   * (List<String>::final_iterator j = sample.features.begin(); j != sample.features.end(); j++) { // cout << *j << " ";
   * // } // cout << endl << endl;
   *
   * return sample; }
   *****************************/

  static double entropy(final ArrayList<Double> v) {
    double sum = 0, maxp = 0;
    for (int i = 0; i < v.size(); i++) {
      if (v.get(i) == 0) continue;
      sum += v.get(i) * log(v.get(i));
      maxp = max(maxp, v.get(i));
    }
    return -maxp;
    // return -sum;//TODO original had 2 return statements?
  }

  int bidir_train(final ArrayList<Sentence> vs, int para) {
    // vme.clear();
    // vme.resize(16);

    for (int t = 0; t < 16; t++) {
      if (t != 15 && t != 0) continue;
      // for (int t = 15; t >= 0; t--) {
      ArrayList<ME_Sample> train = newArrayList(); // TODO check size

      if (para != -1 && t % 4 != para) continue;
      // if (t % 2 == 1) continue;
      // cerr << "type = " << t << endl;
      // cerr << "extracting features...";
      int n = 0;
      for (Sentence s : vs) {
        for (int j = 0; j < s.size(); j++) {
          String pos_left1 = "BOS", pos_left2 = "BOS2";
          if (j >= 1) pos_left1 = s.get(j - 1).pos;
          if (j >= 2) pos_left2 = s.get(j - 2).pos;
          String pos_right1 = "EOS", pos_right2 = "EOS2";
          if (j <= s.size() - 2) pos_right1 = s.get(j + 1).pos;
          if (j <= s.size() - 3) pos_right2 = s.get(j + 2).pos;
          if ((t & 0x8) == 0) pos_left2 = "";
          if ((t & 0x4) == 0) pos_left1 = "";
          if ((t & 0x2) == 0) pos_right1 = "";
          if ((t & 0x1) == 0) pos_right2 = "";

          train.add(mesample(s, j, pos_left2, pos_left1, pos_right1, pos_right2));
        }
        // if (n++ > 1000) break;
      }
      // cerr << "done" << endl;

      ME_Model m = new ME_Model();
      // m.set_heldout(1000,0);
      // m.train(train, 2, 1000, 0);
      m.train(train, 2, 0, 1);
      String filename = "model.bidir." + t;
      m.save_to_file(filename);
    }

    return 0; // jenia: original didn't return explicitly
  }

  /*
   * TODO jenia: A Hypothesis has more information than needed. In particular entropies refer to the maximum probability
   * already and vvp contains in fact all the hypothesis
   */
  static class Hypothesis {
    Sentence sentence;
    ArrayList<Double> entropies;
    ArrayList<Integer> order;
    ArrayList<ArrayList<Tuple2<String, Double>>> vvp;
    double prob;

    // jenia: standard java hasn't multimaps and the argument tagdic is actually never used so it's discarded
    Hypothesis(final Sentence sentence,
        // final multimap<String, String> tagdic,
        final ArrayList<ME_Model> vme) {
      prob = 1.0;
      this.sentence = sentence.copy();
      int n = this.sentence.size();
      entropies = newArrayList(n, 0.0);
      vvp = newArrayList(n, new Constructor<ArrayList<Tuple2<String, Double>>>() {
        @Override
        public ArrayList<Tuple2<String, Double>> neu() {
          return new ArrayList<Tuple2<String, Double>>();
        }
      });
      order = newArrayList(n, 0);
      for (int i = 0; i < n; i++) {
        this.sentence.get(i).prd = "";
        Update(i, vme);
      }
    }

    private Hypothesis() {};

    Hypothesis copy() {
      Hypothesis ret = new Hypothesis();
      ret.sentence = this.sentence.copy();
      /* The following can be done because Double, Integer, and Tuple2<String, Double> are immutable objects */
      ret.entropies = new ArrayList<Double>(this.entropies);
      ret.order = new ArrayList<Integer>(this.order);
      ret.vvp = newArrayList(this.vvp.size());
      for (ArrayList<Tuple2<String, Double>> a : this.vvp) {
        ArrayList<Tuple2<String, Double>> reta = new ArrayList<Tuple2<String, Double>>(a);
        ret.vvp.add(reta);
      }
      ret.prob = this.prob;
      return ret;
    }

    @Override
    public String toString() {
      return "Hypothesis:" + sentence.toString() + "\n" +
          "    " + entropies + "\n" +
          "    " + order + "\n" +
          "    " + vvp + "\n" +
          "    " + prob + "\n";
    }

    final boolean operator_less(final Hypothesis h) {
      return prob < h.prob;
    }

    static final Comparator<Hypothesis> Order = new Comparator<Hypothesis>() {
      @Override
      public int compare(Hypothesis o1, Hypothesis o2) {
        if (o1.prob < o2.prob)
          return -1;
        else if (o1.prob > o2.prob)
          return +1;
        else
          return 0;
      }
    };

    void Print() {
      for (int k = 0; k < sentence.size(); k++) {
        // TODO
        // cout << vt[k].str << "/";
        // if (vt[k].prd.equals("")) cout << "?";
        // else cout << vt[k].prd;
        // cout << " ";
      }
      // cout << endl;
    }

    void Update(final int j,
        // final multimap<String, String> tagdic,
        final ArrayList<ME_Model> vme)
    {
      String pos_left1 = "BOS", pos_left2 = "BOS2";
      if (j >= 1) pos_left1 = sentence.get(j - 1).prd; // maybe bug??
      // if (j >= 1 && !vt[j-1].isEmpty()) pos_left1 = vt[j-1].prd; // this should be correct
      if (j >= 2) pos_left2 = sentence.get(j - 2).prd;
      String pos_right1 = "EOS", pos_right2 = "EOS2";
      if (j <= sentence.size() - 2) pos_right1 = sentence.get(j + 1).prd;
      if (j <= sentence.size() - 3) pos_right2 = sentence.get(j + 2).prd;

      ME_Sample mes = mesample(sentence, j, pos_left2, pos_left1, pos_right1, pos_right2);

      ArrayList<Double> membp;
      ME_Model mep = null;
      int bits = 0;
      if (!pos_left2.isEmpty()) bits += 8;
      if (!pos_left1.isEmpty()) bits += 4;
      if (!pos_right1.isEmpty()) bits += 2;
      if (!pos_right2.isEmpty()) bits += 1;
      assert (bits >= 0 && bits < 16);
      mep = vme.get(bits);
      membp = mep.classify(mes);
      assert (!mes.label.isEmpty());
      entropies.set(j, entropy(membp));
      // vent[j] = -j;

      vvp.get(j).clear();
      double maxp = membp.get(mep.get_class_id(mes.label));
      // vp[j] = mes.label;
      for (int i = 0; i < mep.num_classes(); i++) {
        double p = membp.get(i);
        if (p > maxp * BEAM_WINDOW)
          vvp.get(j).add(Tuple2.$(mep.get_class_label(i), p));
      }
      /*
       * if (tagdic.find(vt[j].str) != tagdic.end()) { // known words String max_tag = ""; double max = 0; for
       * (multimap<String, String>::final_iterator i = tagdic.lower_bound(vt[j].str); i !=
       * tagdic.upper_bound(vt[j].str); i++) { double p = membp[mep->get_class_id(i->second)]; if (p > max) { max = p;
       * max_tag = i->second; } } vp[j] = max_tag; } else { // unknown words vp[j] = mes.label; // vent[j] += 99999; }
       */
    }
  }

  /**
   * jenia tag_dictionary discarded as never used
   *
   * @param order
   * @param h
   * @param vme
   * @param vh
   */
  static void generate_hypotheses(final int order, final Hypothesis h,
      // final multimap<String, String> tag_dictionary,
      final ArrayList<ME_Model> vme,
      List<Hypothesis> vh)
  {
    int n = h.sentence.size();
    int pred_position = -1;
    double min_ent = 999999;
    String pred = "";
    double pred_prob = 0;
    for (int j = 0; j < n; j++) {
      if (!h.sentence.get(j).prd.isEmpty()) continue;
      double ent = h.entropies.get(j);
      if (ent < min_ent) {
        // pred = h.vvp[j].begin()->first;
        // pred_prob = h.vvp[j].begin()->second;
        min_ent = ent;
        pred_position = j;
      }
    }
    assert (pred_position >= 0 && pred_position < n);

    for (Tuple2<String, Double> k : h.vvp.get(pred_position)) {
      Hypothesis newh = h.copy();

      newh.sentence.get(pred_position).prd = k._1;
      newh.order.set(pred_position, order + 1);
      newh.prob = h.prob * k._2;

      // update the neighboring predictions
      for (int j = pred_position - UPDATE_WINDOW_SIZE; j <= pred_position + UPDATE_WINDOW_SIZE; j++) {
        if (j < 0 || j > n - 1) continue;
        if (newh.sentence.get(j).prd.isEmpty()) newh.Update(j, vme);
      }
      vh.add(newh);
    }
  }

  /**
   * tag_dictionary discarded
   *
   * @param sentence
   * @param vme
   */
  static void bidir_decode_beam(Sentence sentence,
      // final multimap<String, String> tag_dictionary,
      final ArrayList<ME_Model> vme)
  {
    int n = sentence.size();
    if (n == 0) return;

    ArrayList<Hypothesis> hypotheses = newArrayList(); // TODO check size
    Hypothesis hyp = new Hypothesis(sentence, vme);
    hypotheses.add(hyp);

    for (int i = 0; i < n; i++) {
      ArrayList<Hypothesis> newHypotheses = newArrayList(); // TODO check size
      for (Hypothesis j : hypotheses) {
        generate_hypotheses(i, j, vme, newHypotheses);
      }
      Collections.sort(newHypotheses, Hypothesis.Order);
      while (newHypotheses.size() > BEAM_NUM) {
        newHypotheses.remove(0);
      }
      hypotheses = newHypotheses;
    }

    hyp = last(hypotheses);
    for (int k = 0; k < n; k++) {
      // cout << h.vt[k].str << "/" << h.vt[k].prd << "/" << h.order[k] << " ";
      sentence.get(k).prd = hyp.sentence.get(k).prd;
    }
    // cout << endl;
  }

  private static void decode_no_context(ArrayList<Token> vt, final ME_Model me_none) {
    int n = vt.size();
    if (n == 0) return;

    for (int i = 0; i < n; i++) {
      ME_Sample mes = mesample(vt, i, "", "", "", "");
      me_none.classify(mes);
      vt.get(i).prd = mes.label;
    }

    for (int k = 0; k < n; k++) {
      // cout << vt[k].str << "/" << vt[k].prd << " ";
    }
    // cout << endl;
  }

  private static class ParenConverter {
    static final Map<String, String> ptb2pos;
    static final Map<String, String> pos2ptb;

    static final List<Tuple2<String, String>> table;

    static {
      table = newArrayList();
      table.add(Tuple2.$("-LRB-", "("));
      table.add(Tuple2.$("-RRB-", ")"));
      table.add(Tuple2.$("-LSB-", "["));
      table.add(Tuple2.$("-RSB-", "]"));
      table.add(Tuple2.$("-LCB-", "{"));
      table.add(Tuple2.$("-RCB-", "}"));

      ptb2pos = new HashMap<String, String>();
      pos2ptb = new HashMap<String, String>();
      for (Tuple2<String, String> elem : table) {
        ptb2pos.put(elem._1, elem._2);
        pos2ptb.put(elem._2, elem._1);
      }
    }

    static String Ptb2Pos(final String s) {
      String ret;
      if ((ret = ptb2pos.get(s)) == null)
        return s;
      else
        return ret;
    }

    static String Pos2Ptb(final String s) {
      String ret;
      if ((ret = pos2ptb.get(s)) == null)
        return s;
      else
        return ret;
    }
  }

  static Sentence bidir_postag(final String line, final ArrayList<ME_Model> vme, final ArrayList<ME_Model> chunking_vme, boolean dont_tokenize) {
    if (line.matches(".*[\n\r\u0085\u2028\u2029].*"))
      throw new IllegalArgumentException("The input line cannot have any line terminator");
    String trimmedLine = line.trim();
    if (trimmedLine.isEmpty()) return new Sentence();

    Sentence sentence = createSentence(line, dont_tokenize, trimmedLine);

    // jenia: final multimap<String, String> dummy;
    // bidir_decode_search(vt, dummy, vme);
    bidir_decode_beam(sentence, vme);
    for (int i = 0; i < sentence.size(); i++) {
      sentence.get(i).pos = sentence.get(i).prd;
    }

    Chunking.bidir_chunking_decode_beam(sentence, chunking_vme);

    NamedEntity.netagging(sentence);

    return sentence;
  }

  private static Sentence createSentence(final String line, boolean dont_tokenize, String trimmedLine) {
    final List<String> lt = (dont_tokenize) ?
        Arrays.asList(trimmedLine.split("\\s+")) // jenia: see genia's README
        :
        tokenize(line);

    Sentence sentence = new Sentence(lt.size());
    for (String slt : lt) {
      // s = ParenConverter.Ptb2Pos(s);
      sentence.add(new Token(slt, "?"));
    }
    return sentence;
  }

  // int push_stop_watch() {
  // static struct timeval start_time, end_time;
  // static boolean start = true;
  // if (start) {
  // gettimeofday(&start_time, NULL);
  // start = false;
  // return 0;
  // }
  //
  // gettimeofday(&end_time, NULL);
  // int elapse_msec = (end_time.tv_sec - start_time.tv_sec) * 1000 +
  // (int)((end_time.tv_usec - start_time.tv_usec) * 0.001);
  // cerr << elapse_msec << " msec" << endl;
  // start = true;
  // return elapse_msec;
  // }

  /**
   * jenia: tag_dictionary discarded
   *
   * @param vs
   * @param vme
   */
  void bidir_postagging(ArrayList<Sentence> vs,
      // final multimap<String, String> tag_dictionary,
      final ArrayList<ME_Model> vme)
  {
    int num_classes = vme.get(0).num_classes();

    // cerr << "now tagging";
    // push_stop_watch();
    int n = 0, ntokens = 0;
    for (Sentence s : vs) {
      ntokens += s.size();
      // if (s.size() > 2) continue;
      bidir_decode_beam(s, vme);
      // bidir_decode_search(s, vme);
      // decode_no_context(s, vme[0]);

      // cout << n << endl;
      /*
       * for (int k = 0; k < s.size(); k++) { cout << s[k].str << "/" << s[k].prd << " "; } cout << endl;
       */
      // if (n > 100) break;

      // if (n++ % 10 == 0) cerr << ".";
    }
    // cerr << endl;
    // int msec = push_stop_watch();
    // cerr << ntokens / (msec/1000.0) << " tokens / sec" << endl;
  }

}
