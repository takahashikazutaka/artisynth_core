package artisynth.demos.tutorial;

import java.awt.Color;

import maspack.matrix.*;
import maspack.render.*;

import artisynth.core.mechmodels.*;
import artisynth.core.materials.*;
import artisynth.core.workspace.RootModel;

/**
 * Simple demo of a particle and rigid body connected by a spring.
 */
public class RigidBodySpring extends RootModel {

   public void build (String[] args) {

      MechModel mech = new MechModel ("mech");
      addModel (mech);

      Particle p1 = new Particle ("p1", /*mass=*/2, 0, 0, 0);
      // create box and set it's pose (position/orientation):
      RigidBody box =
         RigidBody.createBox ("box", 0.5, 0.3, 0.3, /*density=*/20);
      box.setPose (new RigidTransform3d (0.75, 0, 0));
      // create marker point and connect it to the box:
      FrameMarker mkr = new FrameMarker (-0.25, 0, 0);
      mkr.setFrame (box);
      // create the spring:
      AxialSpring spring = new AxialSpring ("spr", /*restLength=*/0);
      spring.setPoints (p1, mkr);
      spring.setMaterial (
         new LinearAxialMaterial (/*stiffness=*/20, /*damping=*/10));

      // add components to the mech model
      mech.addParticle (p1);
      mech.addRigidBody (box);
      mech.addFrameMarker (mkr);
      mech.addAxialSpring (spring);

      p1.setDynamic (false);               // first particle set to be fixed
      mech.setBounds (-1, 0, -1, 1, 0, 0); // increase viewer bounds

      // set render properties for the components
      setPointRenderProps (p1);
      setPointRenderProps (mkr);
      setSpringRenderProps (spring);
   }

   protected void setPointRenderProps (Point p) {
      RenderProps.setPointColor (p, Color.RED);
      RenderProps.setPointStyle (p, RenderProps.PointStyle.SPHERE);
      RenderProps.setPointRadius (p, 0.06);
   }

   protected void setSpringRenderProps (AxialSpring s) {
      RenderProps.setLineColor (s, Color.BLUE);
      RenderProps.setLineStyle (s, RenderProps.LineStyle.CYLINDER);
      RenderProps.setLineRadius (s, 0.02);
   }
}