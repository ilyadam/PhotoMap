package com.ilya.photomap.repository;

import android.util.Log;

import com.google.gson.reflect.TypeToken;
import com.ilya.photomap.App;
import com.ilya.photomap.data.database.AppDatabase;
import com.ilya.photomap.data.database.dao.CommentDao;
import com.ilya.photomap.data.database.entities.Comment;
import com.ilya.photomap.data.network.api.CommentApi;
import com.ilya.photomap.data.network.model.CommentInDTO;
import com.ilya.photomap.data.network.model.CommentOutDTO;
import com.ilya.photomap.util.ListUtil;
import com.ilya.photomap.util.ResponseUtil;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import okhttp3.ResponseBody;

public class CommentRepository {

    /**
     * Database
     */
    private final CommentDao commentDao;

    /**
     * Network
     */
    private final CommentApi commentApi;

    private final int photoId;

    public CommentRepository(int photoId) {
        AppDatabase db = AppDatabase.getInstance();
        commentDao = db.commentDao();
        commentApi = App.getServerService().getCommentApi();

        this.photoId = photoId;
    }

    public Single<List<Comment>> loadComments(int page) {
        return commentApi.getComments(photoId, page) // Network request
                .subscribeOn(Schedulers.io())
                // Mapping responseBody to list of items
                .map(responseBody -> {
                    List<CommentOutDTO> commentsOut = ResponseUtil.parseData(responseBody, new TypeToken<List<CommentOutDTO>>(){}.getType());
                    return ListUtil.map(commentsOut, dto -> dto.convertToEntity(photoId));
                })
                // Inserting new comments to database
                .doAfterSuccess(comments -> Observable.combineLatest(
                        Observable.just(comments),
                        commentDao.getIds().toObservable(), // Getting all existing comments in database (ids)
                        (serverComments, databaseCommentsIds) -> {
                            List<Comment> result = new ArrayList<>();

                            for (Comment comment : serverComments) {
                                if (databaseCommentsIds.contains(comment.id)) continue;
                                result.add(comment);
                            }

                            return result;
                        })
                        .subscribeOn(Schedulers.io())
                        .flatMapCompletable(commentDao::insertAll)
                        .subscribe())
                // Load data from db if error
                .onErrorResumeNext(commentDao.getAll(photoId, page)
                        .flatMap(comments -> {
                            if (comments.size() == 0 && page == 0) return Single.error(new RuntimeException("No data is available, please, refresh the page"));
                            return Single.just(comments);
                        }))
                .observeOn(AndroidSchedulers.mainThread());
    }

    public Single<ResponseBody> addComment(CommentInDTO comment) {
        return commentApi.leaveComment(photoId, comment)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    public Completable deleteComment(int id) {
        return commentApi.deleteComment(photoId, id)
                .subscribeOn(Schedulers.io())
                .andThen(commentDao.delete(id))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

}
