/**
 * Copyright (c) 2014, by the Authors: John E Lloyd (UBC)
 *
 * This software is freely available under a 2-clause BSD license. Please see
 * the LICENSE file in the ArtiSynth distribution directory for details.
 */
package artisynth.core.util;

/**
 * Parsing token that holds a string value.
 */
public class StringToken extends ScanToken {

   String myValue;

   public StringToken (String str) {
      super ();
      myValue = str;
   }

   public StringToken (String str, int lineno) {
      super (lineno);
      myValue = str;
   }

   public String value() {
      return myValue;
   }

   public String toString() {
      return "StringToken['"+value()+"' line "+lineno()+"]";
   }

}
