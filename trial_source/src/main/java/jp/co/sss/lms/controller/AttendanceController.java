package jp.co.sss.lms.controller;

import java.text.ParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import jakarta.servlet.http.HttpSession;
import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.service.StudentAttendanceService;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;

/**
 * 勤怠管理コントローラ
 * 
 * @author 東京ITスクール
 */
@Controller
@RequestMapping("/attendance")
public class AttendanceController {

	@Autowired
	private StudentAttendanceService studentAttendanceService;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	AttendanceUtil attendanceUtil;
	@Autowired
	AttendanceManagementDto attendanceManagementDto;

	/**
	 * 勤怠管理画面 初期表示
	 * 
	 * @param lmsUserId
	 * @param courseId
	 * @param model
	 * @param session
	 * @return 勤怠管理画面
	 * @throws ParseException
	 */
	@RequestMapping(path = "/detail", method = RequestMethod.GET)
	public String index(Model model, HttpSession session) {

		// 勤怠一覧の取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		model.addAttribute("attendanceManagementDtoList", attendanceManagementDtoList);

		//大山忠資-Task.25
		//セッションのログイン情報を取得
		loginUserDto = (LoginUserDto) session.getAttribute("loginUserDto");
		//ログイン情報からlmsユーザーIDを取得
		Integer lmsUserId = loginUserDto.getLmsUserId();
		//現在の日付を取得
		Date trainingDate = attendanceUtil.getTrainingDate();
		//削除フラグをオフにする
		short deleteFlg = Constants.DB_FLG_FALSE;
		//勤怠の未入力件数を取得
		Integer notEnterCount = studentAttendanceService.getNotEnterCount(lmsUserId, deleteFlg, trainingDate);
		//勤怠の未入力がある場合はture,ない場合はfalse
		if (notEnterCount > 0) {
			model.addAttribute("notEnter", true);

		} else {
			model.addAttribute("notEnter", false);
		}

		return "attendance/detail";
	}

	/**
	 * 勤怠管理画面 『出勤』ボタン押下
	 * 
	 * @param model
	 * @return 勤怠管理画面
	 */
	@RequestMapping(path = "/detail", params = "punchIn", method = RequestMethod.POST)
	public String punchIn(Model model) {

		// 更新前のチェック
		String error = studentAttendanceService.punchCheck(Constants.CODE_VAL_ATWORK);
		model.addAttribute("error", error);
		// 勤怠登録
		if (error == null) {
			String message = studentAttendanceService.setPunchIn();
			model.addAttribute("message", message);
		}
		// 一覧の再取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		model.addAttribute("attendanceManagementDtoList", attendanceManagementDtoList);

		return "attendance/detail";
	}

	/**
	 * 勤怠管理画面 『退勤』ボタン押下
	 * 
	 * @param model
	 * @return 勤怠管理画面
	 */
	@RequestMapping(path = "/detail", params = "punchOut", method = RequestMethod.POST)
	public String punchOut(Model model) {

		// 更新前のチェック
		String error = studentAttendanceService.punchCheck(Constants.CODE_VAL_LEAVING);
		model.addAttribute("error", error);
		// 勤怠登録
		if (error == null) {
			String message = studentAttendanceService.setPunchOut();
			model.addAttribute("message", message);
		}
		// 一覧の再取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		model.addAttribute("attendanceManagementDtoList", attendanceManagementDtoList);

		return "attendance/detail";
	}

	/**
	 * 勤怠管理画面 『勤怠情報を直接編集する』リンク押下
	 * 
	 * @param model
	 * @return 勤怠情報直接変更画面
	 */
	@SuppressWarnings("null")
	@RequestMapping(path = "/update")
	public String update(Model model) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		// 勤怠フォームの生成
		AttendanceForm attendanceForm = studentAttendanceService
				.setAttendanceForm(attendanceManagementDtoList);

		//大山忠資 - Task.26
		//出勤、退勤時間のマップ(時)を生成
		LinkedHashMap<Integer, String> hourMap = attendanceUtil.getHourMap();
		//出勤、退勤時間のマップ(分)を生成
		LinkedHashMap<Integer, String> minuteMap = attendanceUtil.getMinuteMap();
		attendanceForm.setHourMap(hourMap);
		attendanceForm.setMinuteMap(minuteMap);

		model.addAttribute("attendanceForm", attendanceForm);

		return "attendance/update";
	}

	/**
	 * 勤怠情報直接変更画面 『更新』ボタン押下
	 * 
	 * @param attendanceForm
	 * @param model
	 * @param result
	 * @return 勤怠管理画面
	 * @throws ParseException
	 */
	@RequestMapping(path = "/update", params = "complete", method = RequestMethod.POST)
	public String complete(AttendanceForm attendanceForm, Model model, BindingResult result)
			throws ParseException {

		//大山忠資 - Task.26
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
			trainingStartTimeHour =  studentAttendanceService.addZero(trainingStartTimeHour);
			trainingStartTimeMinute =  studentAttendanceService.addZero(trainingStartTimeMinute);
			trainingEndTimeHour =  studentAttendanceService.addZero(trainingEndTimeHour);
			trainingEndTimeMinute =  studentAttendanceService.addZero(trainingEndTimeMinute);
			//(時)と(分)を結合する
			String trainingStartTime = trainingStartTimeHour + trainingStartTimeMinute;
			String trainingEndTime = trainingEndTimeHour + trainingEndTimeMinute;
			attendanceForm.getAttendanceList().get(i).setTrainingStartTime(trainingStartTime);
			attendanceForm.getAttendanceList().get(i).setTrainingEndTime(trainingEndTime);
		}

		// 更新
		String message = studentAttendanceService.update(attendanceForm);
		model.addAttribute("message", message);
		// 一覧の再取得
		List<AttendanceManagementDto> attendanceManagementDtoList = studentAttendanceService
				.getAttendanceManagement(loginUserDto.getCourseId(), loginUserDto.getLmsUserId());
		model.addAttribute("attendanceManagementDtoList", attendanceManagementDtoList);

		return "attendance/detail";
	}

	

}