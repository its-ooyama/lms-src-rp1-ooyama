package jp.co.sss.lms.service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
			dailyAttendanceForm
					.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());
			//大山忠資_Task.26
			dailyAttendanceForm
					.setTrainingStartTimeHour(attendanceUtil.getHour(attendanceManagementDto.getTrainingStartTime()));
			dailyAttendanceForm
					.setTrainingStartTimeMinute(
							attendanceUtil.getMinute(attendanceManagementDto.getTrainingStartTime()));
			dailyAttendanceForm
					.setTrainingEndTimeHour(attendanceUtil.getHour(attendanceManagementDto.getTrainingEndTime()));
			dailyAttendanceForm
					.setTrainingEndTimeMinute(attendanceUtil.getMinute(attendanceManagementDto.getTrainingEndTime()));
			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		return attendanceForm;
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			// 出勤時刻整形
			TrainingTime trainingStartTime = null;
			trainingStartTime = new TrainingTime(dailyAttendanceForm.getTrainingStartTime());
			tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			// 退勤時刻整形
			TrainingTime trainingEndTime = null;
			trainingEndTime = new TrainingTime(dailyAttendanceForm.getTrainingEndTime());
			tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠情報の未入力の件数
	 * 
	 * @author 大山 忠資 - Task.25
	 * @param lmsUserId
	 * @param deleteFlg
	 * @param trainingDate
	 * @return Integer
	 */
	public Integer getNotEnterCount() {

		//ログイン情報からlmsユーザーIDを取得
		Integer lmsUserId = loginUserDto.getLmsUserId();
		//現在の日付を取得
		Date trainingDate = attendanceUtil.getTrainingDate();
		//削除フラグをオフにする
		short deleteFlg = Constants.DB_FLG_FALSE;

		//データベースから未入力件数を取得
		Integer notEnterCount = tStudentAttendanceMapper.notEnterCount(lmsUserId, deleteFlg, trainingDate);

		return notEnterCount;
	}

	/**
	 * String型の一桁の数値を%02dの形にするメソッド
	 * 
	 * @author 大山 忠資 - Task.26
	 * @param trainingTime
	 * @return String型の%02d型の数字
	 */
	public String addZero(String trainingTime) {
		if (trainingTime.length() == 1) {
			trainingTime = "0" + trainingTime;
		}
		return trainingTime;
	}

	/**
	 * (時)と(分)に分割した時刻を結合して勤怠フォームにセットするメソッド
	 * 
	 * @author 大山 忠資 - Task.26
	 * @param attendanceForm
	 * @return 勤怠Form
	 */
	public AttendanceForm clockInOut(AttendanceForm attendanceForm) {
		//勤怠フォームに出勤時間、退勤時間をセット
		for (int i = 0; i < attendanceForm.getAttendanceList().size(); i++) {
			String trainingStartTimeHour = Objects
					.toString(attendanceForm.getAttendanceList().get(i).getTrainingStartTimeHour(), "");
			String trainingStartTimeMinute = Objects
					.toString(attendanceForm.getAttendanceList().get(i).getTrainingStartTimeMinute(), "");
			String trainingEndTimeHour = Objects
					.toString(attendanceForm.getAttendanceList().get(i).getTrainingEndTimeHour(), "");
			String trainingEndTimeMinute = Objects
					.toString(attendanceForm.getAttendanceList().get(i).getTrainingEndTimeMinute(), "");
			//String型に変更した数値を%02dの形に変更する
			trainingStartTimeHour = addZero(trainingStartTimeHour);
			trainingStartTimeMinute = addZero(trainingStartTimeMinute);
			trainingEndTimeHour = addZero(trainingEndTimeHour);
			trainingEndTimeMinute = addZero(trainingEndTimeMinute);
			//(時)と(分)を結合する
			String trainingStartTime = trainingStartTimeHour + trainingStartTimeMinute;
			String trainingEndTime = trainingEndTimeHour + trainingEndTimeMinute;
			attendanceForm.getAttendanceList().get(i).setTrainingStartTime(trainingStartTime);
			attendanceForm.getAttendanceList().get(i).setTrainingEndTime(trainingEndTime);
		}

		return attendanceForm;
	}

	/**
	 * 勤怠情報直接変更画面の入力チェック
	 * 
	 * @author 大山 忠資 - Task.27
	 * @param attendanceForm
	 */
	public void inputErrorCheck(AttendanceForm attendanceForm, BindingResult result) {
		for (int i = 0; i < attendanceForm.getAttendanceList().size(); i++) {
			//備考の入力文字数のチェック(100字以上)
			String note = attendanceForm.getAttendanceList().get(i).getNote();
			if (note.length() > 100) {
				result.addError(new FieldError(result.getObjectName(), "attendanceList[" + i + "].note",
						messageUtil.getMessage("maxlength", new String[] { "備考", "100" })));
			}

			//出退勤時間の入力チェック
			Integer trainingStartTimeHour = attendanceForm.getAttendanceList().get(i).getTrainingStartTimeHour();
			Integer trainingStartTimeMinute = attendanceForm.getAttendanceList().get(i).getTrainingStartTimeMinute();
			Integer trainingEndTimeHour = attendanceForm.getAttendanceList().get(i).getTrainingEndTimeHour();
			Integer trainingEndTimeMinute = attendanceForm.getAttendanceList().get(i).getTrainingEndTimeMinute();
			//中抜け時間
			Integer blankTime = attendanceForm.getAttendanceList().get(i).getBlankTime();

			//出勤時間の(時)が空欄、(分)に値が入っているとき
			if (trainingStartTimeHour == null && trainingStartTimeMinute != null) {
				result.addError(
						new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingStartTimeHour",
								messageUtil.getMessage("input.invalid", new String[] { "出勤時間" })));
			}
			//出勤時間の(分)が空欄、(時)に値が入っているとき
			if (trainingStartTimeHour != null && trainingStartTimeMinute == null) {
				result.addError(
						new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingStartTimeMinute",
								messageUtil.getMessage("input.invalid", new String[] { "出勤時間" })));
			}
			//退勤時間の(時)が空欄、(分)に値が入っているとき
			if (trainingEndTimeHour == null && trainingEndTimeMinute != null) {
				result.addError(new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingEndTimeHour",
						messageUtil.getMessage("input.invalid", new String[] { "退勤時間" })));
			}
			//退勤時間の(分)が空欄、(時)に値が入っているとき
			if (trainingEndTimeHour != null && trainingEndTimeMinute == null) {
				result.addError(
						new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingEndTimeMinute",
								messageUtil.getMessage("input.invalid", new String[] { "退勤時間" })));
			}
			//出勤時間に入力なし、退勤時間に入力ありの場合
			if (trainingStartTimeHour == null && trainingStartTimeMinute == null
					&& trainingEndTimeHour != null && trainingEndTimeMinute != null) {
				result.addError(
						new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingStartTimeHour",
								messageUtil.getMessage("attendance.punchInEmpty")));
			}

			//出勤時間、退勤時間がすべて入力されている場合
			if (trainingStartTimeHour != null && trainingStartTimeMinute != null
					&& trainingEndTimeHour != null && trainingEndTimeMinute != null) {
				//出勤時間が退勤時間より遅い時刻が入力されている場合
				String trainingStartTime = addZero(trainingStartTimeHour.toString()) + ":"
						+ addZero(trainingStartTimeMinute.toString());
				String trainingEndTime = addZero(trainingEndTimeHour.toString()) + ":"
						+ addZero(trainingEndTimeMinute.toString());
				if (trainingStartTimeHour > trainingEndTimeHour) {
					result.addError(
							new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingStartTimeHour",
									messageUtil.getMessage("attendance.trainingTimeRange",
											new String[] { trainingEndTime, trainingStartTime })));
				} else if (trainingStartTimeHour == trainingEndTimeHour
						&& trainingStartTimeMinute > trainingEndTimeMinute) {
					result.addError(
							new FieldError(result.getObjectName(), "attendanceList[" + i + "].trainingStartTimeMinute",
									messageUtil.getMessage("attendance.trainingTimeRange",
											new String[] { trainingEndTime, trainingStartTime })));
				}

				//勤務時間を分で計算する
				Integer trainingTime = (trainingEndTimeHour * 60 + trainingEndTimeMinute)
						- (trainingStartTimeHour * 60 + trainingStartTimeMinute);
				//中抜け時間が勤務時間を超える場合
				if (blankTime != null && trainingTime < blankTime) {
					result.addError(new FieldError(result.getObjectName(), "attendanceList[" + i + "].blankTime",
							messageUtil.getMessage("attendance.blankTimeError")));
				}
			}

		}
	}

}
