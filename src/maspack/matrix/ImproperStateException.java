/**
 * Copyright (c) 2014, by the Authors: John E Lloyd (UBC)
 *
 * This software is freely available under a 2-clause BSD license. Please see
 * the LICENSE file in the ArtiSynth distribution directory for details.
 */
package maspack.matrix;

public class ImproperStateException extends RuntimeException {
   private static final long serialVersionUID = 1L;

   public ImproperStateException() {
      super();
   }

   public ImproperStateException (String msg) {
      super (msg);
   }
}
