package com.example.ericpeng.drawertest;

import android.app.Application;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by ericpeng on 5/8/17.
 */

public class GlobalVars extends Application{
    private static List<String> globalVar;

    public GlobalVars(){
       globalVar = new ArrayList<>();
    }

    public static List<String> getGlobalVarValue() {
        return globalVar;
    }

    public static void setGlobalVar(String str) {
        globalVar.add(str);
        for (int k = 0; k < globalVar.size(); k++){
            System.out.println(globalVar.get(k));
            System.out.println("GV");
            System.out.println(globalVar.size());
        }

    }
}
