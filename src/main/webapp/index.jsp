<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<body>
<h2>Hello World!</h2>

<form name="=form1" action="/manage/product/upload.do" method="post" enctype="multipart/form-data">

<input type="file" name="upload_file">
<input type="submit" value="上传">
</form>




<form name="=form1" action="/manage/product/richtext_img_upload.do" method="post" enctype="multipart/form-data">

    <input type="file" name="upload_file">
    <input type="submit" value="富文本上传">
</form>
</body>
</html>
