package com.example.dominik.project3;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import io.reactivex.Observable;

import static org.junit.Assert.*;

public class MainActivityTest {
    @Test
    public void clamp() throws Exception {
    }

    @Test
    public void rotate() throws Exception {
    }

    @Test
    public void getLast() throws Exception {
        List<Tuple3d> list = new ArrayList<>(Collections.nCopies(100,new Vector3d()));
        for (int i = 0; i < list.size(); i++) {
            list.set(i, new Vector3d(i,i,i));
        }
        List<Tuple3d> l = MainActivity.getLast(50, 49, list);
        assertEquals(l.size(), 50);
        assertEquals(l.get(0), new Vector3d(0,0,0));
        assertEquals(l.get(49), new Vector3d(49,49,49));
    }

    @Test
    public void average3() throws Exception {

    }

}