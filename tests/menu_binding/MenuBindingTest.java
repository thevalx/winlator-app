import android.view.KeyEvent;
import com.winlator.core.ControllerMenuInputGate;
import com.winlator.inputcontrols.Binding;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.ExternalController;
import com.winlator.inputcontrols.ExternalControllerBinding;
import com.winlator.xserver.XKeycode;

public final class MenuBindingTest {
    private static final int DOWN=0,UP=1,MODE=110,A=96,B=97;
    private static int checks;

    private static void check(boolean condition,String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }

    private static KeyEvent event(int action,int code,int repeat,int device) {
        return new KeyEvent(action,code,repeat,device);
    }

    private static ExternalControllerBinding mapping(int code,Binding action) {
        ExternalControllerBinding result=new ExternalControllerBinding();
        result.setKeyCode(code);
        result.setBinding(action);
        return result;
    }

    private static void mappings() {
        check(Binding.WINLATOR_MENU.ordinal()==Binding.KEY_VOL_DOWN.ordinal()+1,"new enum appended, old ordinals retained");
        check(Binding.values()[Binding.values().length-1]==Binding.WINLATOR_MENU,"menu action stays last");
        check(Binding.WINLATOR_MENU.isWinlator(),"Winlator category");
        check(!Binding.NONE.isWinlator(),"NONE has no implicit menu action");
        check(!Binding.WINLATOR_MENU.isGamepad(),"no Windows gamepad action");
        check(!Binding.WINLATOR_MENU.isKeyboard(),"no Windows key action");
        check(!Binding.WINLATOR_MENU.isMouse(),"no Windows pointer action");
        check(Binding.WINLATOR_MENU.keycode==XKeycode.KEY_NONE,"no X keyboard code");
        check(Binding.WINLATOR_MENU.getPointerButton()==null,"no mouse button");
        check(Binding.fromString("WINLATOR_MENU")==Binding.WINLATOR_MENU,"profile name roundtrip");
        check(Binding.WINLATOR_MENU.toString().equals("Open in-game menu"),"action label");
        check(Binding.winlatorBindingValues().length==2,"explicit NONE and menu options");
        check(Binding.winlatorBindingValues()[0]==Binding.NONE,"category selection stays opt-in");
        check(Binding.winlatorBindingValues()[1]==Binding.WINLATOR_MENU,"menu choice");
        for(int i=0;i<Binding.winlatorBindingValues().length;i++) {
            check(Binding.winlatorBindingLabels()[i].equals(Binding.winlatorBindingValues()[i].toString()),"label/value alignment");
        }
        ExternalControllerBinding saved=mapping(MODE,Binding.WINLATOR_MENU);
        check(saved.toJSONObject().get("binding").equals("WINLATOR_MENU"),"serialized profile enum name");
        check(((Number)saved.toJSONObject().get("keyCode")).intValue()==MODE,"serialized physical key");
        check(new ExternalControllerBinding().getBinding()==Binding.NONE,"new physical row defaults unbound");

        MenuInputFixture input=new MenuInputFixture();
        int[] toggles={0};
        input.setOnOpenMenu(()->toggles[0]++);
        check(!input.handleMenuKeyEvent(event(DOWN,MODE,0,1)),"no active profile leaves Mode unchanged");
        input.profile=new ControlsProfile();
        ExternalController controller=new ExternalController();
        input.profile.controllers.put(1,controller);
        check(!input.handleMenuKeyEvent(event(DOWN,MODE,0,1)),"unmapped Mode unchanged");
        controller.mappings.put(MODE,mapping(MODE,Binding.NONE));
        check(!input.handleMenuKeyEvent(event(DOWN,MODE,0,1)),"explicit NONE unchanged");
        controller.mappings.put(MODE,saved);
        check(input.handleMenuKeyEvent(new KeyEvent(DOWN,MODE,0,1,true)),"canceled configured down consumed");
        check(toggles[0]==0,"canceled down never toggles menu");
        check(input.handleMenuKeyEvent(event(UP,MODE,0,1)),"canceled press release consumed");
        check(!input.handleMenuKeyEvent(event(DOWN,MODE,0,2)),"other controller not captured");
        check(!input.handleMenuKeyEvent(event(DOWN,A,0,1)),"other controller key unchanged");
        check(input.handleMenuKeyEvent(event(DOWN,MODE,0,1)),"configured down consumed");
        check(toggles[0]==1,"configured press toggles");
        for(int repeat=1;repeat<=20;repeat++) {
            check(input.handleMenuKeyEvent(event(DOWN,MODE,repeat,1)),"repeat consumed");
            check(toggles[0]==1,"repeat never toggles");
        }
        check(input.handleMenuKeyEvent(event(DOWN,MODE,0,1)),"duplicate down consumed");
        check(toggles[0]==1,"duplicate down never toggles");
        check(input.handleMenuKeyEvent(event(UP,MODE,0,1)),"configured up consumed");
        check(input.handleMenuKeyEvent(event(DOWN,MODE,0,1)),"next press consumed");
        check(toggles[0]==2,"same key closes menu on next press");
        input.onInputFocusLost();
        check(input.handleMenuKeyEvent(event(DOWN,MODE,3,1)),"focus loss retains repeat suppression");
        check(toggles[0]==2,"focus loss repeats do not toggle");
        check(input.handleMenuKeyEvent(event(UP,MODE,0,1)),"focus loss retained up");
        check(input.handleMenuKeyEvent(event(DOWN,MODE,0,1)),"fresh press after focus loss");
        check(toggles[0]==3,"fresh press works");
        input.profile=null;
        check(input.handleMenuKeyEvent(event(UP,MODE,0,1)),"profile removal drains held up");
        check(!input.handleMenuKeyEvent(event(DOWN,MODE,0,1)),"profile removal disables shortcut");
        input.profile=new ControlsProfile();
        input.profile.controllers.put(1,controller);
        input.editMode=true;
        check(!input.handleMenuKeyEvent(event(DOWN,MODE,0,1)),"editor never executes mapping");
        input.editMode=false;
        controller.mappings.put(MODE,mapping(MODE,Binding.KEY_ESC));
        check(!input.handleMenuKeyEvent(event(DOWN,MODE,0,1)),"changing mapping restores normal game path");
    }

    private static boolean blocked(ControllerMenuInputGate gate,int action,int code,int repeat,int device,boolean menu) {
        return gate.shouldBlock(action,code,repeat,device,true,menu);
    }

    private static void gate() {
        ControllerMenuInputGate gate=new ControllerMenuInputGate();
        check(!blocked(gate,DOWN,A,0,1,false),"ordinary game down allowed");
        check(!blocked(gate,DOWN,A,1,1,false),"ordinary game repeat allowed");
        gate.onMenuOpened();
        for(int repeat=1;repeat<=20;repeat++) check(blocked(gate,DOWN,A,repeat,1,false),"pre-menu held repeats blocked after close");
        check(blocked(gate,UP,A,0,1,false),"pre-menu held up blocked after close");
        check(!blocked(gate,DOWN,A,0,1,false),"fresh press allowed");
        check(!blocked(gate,UP,A,0,1,false),"fresh release allowed");
        check(blocked(gate,DOWN,B,0,1,true),"menu navigation down blocked from game");
        check(blocked(gate,UP,B,0,1,false),"menu navigation up blocked after close");
        check(blocked(gate,DOWN,B,0,1,true),"second navigation down blocked");
        check(blocked(gate,UP,B,0,1,true),"navigation up while menu open");
        check(!blocked(gate,DOWN,B,0,1,false),"new press after navigation release allowed");
        check(!blocked(gate,DOWN,A,0,2,false),"second controller tracked separately");
        gate.onFocusLost();
        check(blocked(gate,UP,A,0,2,false),"second controller held up drained");
        check(blocked(gate,DOWN,B,5,1,false),"first controller held repeat drained");
        check(!blocked(gate,DOWN,B,0,1,false),"fresh press recovers lost release");
        check(!blocked(gate,UP,B,0,1,false),"recovered press releases");
        check(blocked(gate,DOWN,A,1,3,false),"orphan repeat blocked");
        check(!gate.shouldBlock(DOWN,19,0,4,false,true),"keyboard never gated as controller");
        check(!gate.shouldBlock(DOWN,A,0,-1,true,true),"virtual/unavailable device never tracked");
        check(!blocked(gate,DOWN,A,0,5,false),"removable device tracked");
        gate.onFocusLost();
        gate.removeDevice(5);
        check(!blocked(gate,UP,A,0,5,false),"removed device state cleared");
        gate.reset();
        check(!blocked(gate,DOWN,A,0,1,false),"reset allows fresh press");
    }

    public static void main(String[] args) {
        mappings();
        gate();
        System.out.println("Menu bindings: "+checks+" checks passed");
    }
}
