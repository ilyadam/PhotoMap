package com.ilya.photomap.ui.screens.main.map;

import com.ilya.photomap.data.database.entities.Photo;
import com.ilya.photomap.ui.base.BaseView;
import com.ilya.photomap.ui.base.InfoView;

import java.util.List;

import io.reactivex.Single;

public interface MapView extends BaseView, InfoView {

    void displayMarkers(List<Photo> photos);
    void refreshMarkers(boolean saveToDatabase);
    void removeMarker(int idPhoto);

}
