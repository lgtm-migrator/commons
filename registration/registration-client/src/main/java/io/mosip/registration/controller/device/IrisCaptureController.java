package io.mosip.registration.controller.device;

import static io.mosip.registration.constants.LoggerConstants.LOG_REG_IRIS_CAPTURE_CONTROLLER;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.StringUtils;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.AuditEvent;
import io.mosip.registration.constants.AuditReferenceIdTypes;
import io.mosip.registration.constants.Components;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.BaseController;
import io.mosip.registration.controller.reg.RegistrationController;
import io.mosip.registration.controller.reg.UserOnboardParentController;
import io.mosip.registration.dto.biometric.BiometricExceptionDTO;
import io.mosip.registration.dto.biometric.IrisDetailsDTO;
import io.mosip.registration.exception.RegBaseCheckedException;
import io.mosip.registration.exception.RegBaseUncheckedException;
import io.mosip.registration.mdm.service.impl.MosipBioDeviceManager;
import io.mosip.registration.service.bio.BioService;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * This is the {@link Controller} class for capturing the Iris image
 * 
 * @author Balaji Sridharan
 * @since 1.0.0
 */
@Controller
public class IrisCaptureController extends BaseController {

	private static final Logger LOGGER = AppConfig.getLogger(IrisCaptureController.class);

	@FXML
	private ImageView leftIrisImage;
	@FXML
	private Label rightIrisThreshold;
	@FXML
	private Label leftIrisQualityScore;
	@FXML
	private GridPane rightIrisPane;
	@FXML
	private GridPane leftIrisPane;
	@FXML
	private Label leftIrisThreshold;
	@FXML
	private Label rightIrisQualityScore;
	@FXML
	private ImageView rightIrisImage;
	@FXML
	private Button scanIris;
	@FXML
	private ProgressBar irisProgress;
	@FXML
	private Label irisQuality;
	@FXML
	private HBox irisRetryBox;
	@FXML
	private Label leftIrisAttempts;
	@FXML
	private Label rightIrisAttempts;
	@FXML
	private ColumnConstraints thresholdPane1;
	@FXML
	private ColumnConstraints thresholdPane2;
	@FXML
	private Label thresholdLabel;
	@FXML
	private AnchorPane rightEyeTrackerImg;
	@FXML
	private AnchorPane leftEyeTrackerImg;

	@Autowired
	private RegistrationController registrationController;
	@Autowired
	private ScanPopUpViewController scanPopUpViewController;
	@Autowired
	private BioService bioservice;

	@Autowired
	private UserOnboardParentController userOnboardParentController;

	@Autowired
	private FaceCaptureController faceCaptureController;

	@FXML
	private Label registrationNavlabel;
	@FXML
	private Label rightIrisException;
	@FXML
	private Label leftIrisException;

	private Pane selectedIris;

	@FXML
	private Button continueBtn;

	@FXML
	private Button backBtn;

	@Autowired
	MosipBioDeviceManager mosipBioDeviceManager;

	/**
	 * This method is invoked when IrisCapture FXML page is loaded. This method
	 * initializes the Iris Capture page.
	 */
	@FXML
	public void initialize() {

		try {
			LOGGER.info(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					"Initializing Iris Capture page for user registration");
			if (getRegistrationDTOFromSession() != null
					&& getRegistrationDTOFromSession().getSelectionListDTO() != null) {
				registrationNavlabel.setText(ApplicationContext.applicationLanguageBundle()
						.getString(RegistrationConstants.UIN_UPDATE_UINUPDATENAVLBL));
			}
			if (getRegistrationDTOFromSession() != null
					&& getRegistrationDTOFromSession().getRegistrationMetaDataDTO().getRegistrationCategory() != null
					&& getRegistrationDTOFromSession().getRegistrationMetaDataDTO().getRegistrationCategory()
							.equals(RegistrationConstants.PACKET_TYPE_LOST)) {
				registrationNavlabel.setText(
						ApplicationContext.applicationLanguageBundle().getString(RegistrationConstants.LOSTUINLBL));
			}

			continueBtn.setDisable(true);

			// Set Threshold
			String irisThreshold = getValueFromApplicationContext(RegistrationConstants.IRIS_THRESHOLD);
			leftIrisThreshold.setText(irisThreshold.concat(RegistrationConstants.PERCENTAGE));
			rightIrisThreshold.setText(irisThreshold.concat(RegistrationConstants.PERCENTAGE));

			if (!(boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {

				for (int attempt = 0; attempt < Integer
						.parseInt(getValueFromApplicationContext(RegistrationConstants.IRIS_RETRY_COUNT)); attempt++) {
					Label label = new Label();
					label.getStyleClass().add(RegistrationConstants.QUALITY_LABEL_GREY);
					label.setId(RegistrationConstants.RETRY_ATTEMPT_ID + (attempt + 1));
					label.setVisible(true);
					label.setText(String.valueOf(attempt + 1));
					label.setAlignment(Pos.CENTER);
					irisRetryBox.getChildren().add(label);
				}
				irisProgress.setProgress(0);

				thresholdPane1.setPercentWidth(Double.parseDouble(irisThreshold));
				thresholdPane2.setPercentWidth(100.00 - (Double.parseDouble(irisThreshold)));
				thresholdLabel.setAlignment(Pos.CENTER);
				thresholdLabel.setText(thresholdLabel.getText().concat("  ").concat(irisThreshold)
						.concat(RegistrationConstants.PERCENTAGE));
			}

			// Disable Scan button
			scanIris.setDisable(true);

			// Display the Captured Iris
			if (getBiometricDTOFromSession() != null || getRegistrationDTOFromSession() != null) {
				displayCapturedIris();
			}

			LOGGER.info(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					"Initializing Iris Capture page for user registration completed");
		} catch (RuntimeException runtimeException) {

			throw new RegBaseUncheckedException(RegistrationConstants.USER_REG_IRIS_CAPTURE_PAGE_LOAD_EXP,
					String.format("Exception while initializing Iris Capture page for user registration  %s",
							ExceptionUtils.getStackTrace(runtimeException)));
		}

	}

	/**
	 * Populate exception.
	 */
	private void populateException() {

		leftIrisException.setText(RegistrationConstants.HYPHEN);
		rightIrisException.setText(RegistrationConstants.HYPHEN);

		if ((boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
			if (getBiometricDTOFromSession() != null && getBiometricDTOFromSession().getOperatorBiometricDTO() != null
					&& getBiometricDTOFromSession().getOperatorBiometricDTO().getBiometricExceptionDTO() != null) {
				getBiometricDTOFromSession().getOperatorBiometricDTO().getBiometricExceptionDTO().stream()
						.forEach(bio -> setExceptionIris(bio));
			}
		} else if (getRegistrationDTOFromSession().isUpdateUINChild()) {
			if (getRegistrationDTOFromSession() != null && getRegistrationDTOFromSession().getBiometricDTO() != null
					&& getRegistrationDTOFromSession().getBiometricDTO().getIntroducerBiometricDTO() != null
					&& getRegistrationDTOFromSession().getBiometricDTO().getIntroducerBiometricDTO()
							.getBiometricExceptionDTO() != null) {

				getRegistrationDTOFromSession().getBiometricDTO().getIntroducerBiometricDTO().getBiometricExceptionDTO()
						.stream().forEach(bio -> setExceptionIris(bio));
			}
		} else {
			if (getRegistrationDTOFromSession() != null && getRegistrationDTOFromSession().getBiometricDTO() != null
					&& getRegistrationDTOFromSession().getBiometricDTO().getApplicantBiometricDTO() != null
					&& getRegistrationDTOFromSession().getBiometricDTO().getApplicantBiometricDTO()
							.getBiometricExceptionDTO() != null) {

				getRegistrationDTOFromSession().getBiometricDTO().getApplicantBiometricDTO().getBiometricExceptionDTO()
						.stream().forEach(bio -> setExceptionIris(bio));
			}
		}

		singleBiometricCaptureCheck();

	}

	private void setExceptionIris(BiometricExceptionDTO bio) {
		if (bio.getBiometricType().equalsIgnoreCase(RegistrationConstants.IRIS.toLowerCase())
				&& bio.getMissingBiometric().equalsIgnoreCase(
						RegistrationConstants.LEFT.toLowerCase().concat(RegistrationConstants.EYE.toLowerCase()))) {
			leftIrisException
					.setText(ApplicationContext.applicationLanguageBundle().getString(bio.getMissingBiometric()));
		} else if (bio.getBiometricType().equalsIgnoreCase(RegistrationConstants.IRIS.toLowerCase())
				&& bio.getMissingBiometric().equalsIgnoreCase(
						RegistrationConstants.RIGHT.toLowerCase().concat(RegistrationConstants.EYE.toLowerCase()))) {
			rightIrisException
					.setText(ApplicationContext.applicationLanguageBundle().getString(bio.getMissingBiometric()));
		}
	}

	private void displayCapturedIris() {
		for (IrisDetailsDTO capturedIris : getIrises()) {
			if (capturedIris.getIrisType().contains(RegistrationConstants.LEFT)) {
				leftIrisImage.setImage(convertBytesToImage(capturedIris.getIris()));
				leftIrisQualityScore.setText(getQualityScore(capturedIris.getQualityScore()));
			} else if (capturedIris.getIrisType().contains(RegistrationConstants.RIGHT)) {
				rightIrisImage.setImage(convertBytesToImage(capturedIris.getIris()));
				rightIrisQualityScore.setText(getQualityScore(capturedIris.getQualityScore()));
			}
		}
	}

	/**
	 * This event handler will be invoked when left iris or right iris
	 * {@link Pane} is clicked.
	 * 
	 * @param mouseEvent
	 *            the triggered {@link MouseEvent} object
	 */
	@FXML
	private void enableScan(MouseEvent mouseEvent) {
		try {
			LOGGER.info(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					"Enabling scan button for user registration");

			Pane sourcePane = (Pane) mouseEvent.getSource();
			sourcePane.requestFocus();
			selectedIris = sourcePane;
			scanIris.setDisable(true);

			if (!(boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {

				if (getSelectedIris().equals(RegistrationConstants.LEFT)) {
					leftEyeTrackerImg.setVisible(true);
					rightEyeTrackerImg.setVisible(false);
				} else {
					rightEyeTrackerImg.setVisible(true);
					leftEyeTrackerImg.setVisible(false);
				}
				irisProgress.setProgress(0);

				for (int attempt = 0; attempt < Integer
						.parseInt(getValueFromApplicationContext(RegistrationConstants.IRIS_RETRY_COUNT)); attempt++) {
					irisRetryBox.lookup(RegistrationConstants.RETRY_ATTEMPT + (attempt + 1)).getStyleClass().clear();
					irisRetryBox.lookup(RegistrationConstants.RETRY_ATTEMPT + (attempt + 1)).getStyleClass()
							.add(RegistrationConstants.QUALITY_LABEL_GREY);
				}
			}
			// Get the Iris from RegistrationDTO based on selected Iris Pane
			IrisDetailsDTO irisDetailsDTO = getIrisBySelectedPane().findFirst().orElse(null);

			boolean isExceptionIris = getIrisExceptions().stream()
					.anyMatch(exceptionIris -> StringUtils.containsIgnoreCase(exceptionIris.getMissingBiometric(),
							(StringUtils.containsIgnoreCase(selectedIris.getId(), RegistrationConstants.LEFT)
									? RegistrationConstants.LEFT
									: RegistrationConstants.RIGHT).concat(RegistrationConstants.EYE)));

			// Enable the scan button, if any of the following satisfies
			// 1. If Iris was not scanned
			// 2. Quality score of the scanned image is less than threshold and
			// number of
			// retries is less than configured
			// 3. If iris is not forced captured
			// 4. If iris is an exception iris
			if (!isExceptionIris && (irisDetailsDTO == null
					|| (Double.compare(irisDetailsDTO.getQualityScore(),
							Double.parseDouble(
									getValueFromApplicationContext(RegistrationConstants.IRIS_THRESHOLD))) < 0
							&& irisDetailsDTO.getNumOfIrisRetry() < Integer
									.parseInt(getValueFromApplicationContext(RegistrationConstants.IRIS_RETRY_COUNT)))
					|| irisDetailsDTO.isForceCaptured())) {
				scanIris.setDisable(false);
			}
			if (!(boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
				irisProgress.setProgress(irisDetailsDTO != null ? irisDetailsDTO.getQualityScore() / 100 : 0);
				irisQuality.setText(irisDetailsDTO != null ? String.valueOf((int) irisDetailsDTO.getQualityScore())
						.concat(RegistrationConstants.PERCENTAGE) : RegistrationConstants.EMPTY);

				if (irisDetailsDTO != null) {
					updateRetriesBox(irisDetailsDTO.getQualityScore(),
							Double.parseDouble(getValueFromApplicationContext(RegistrationConstants.IRIS_THRESHOLD)),
							irisDetailsDTO.getNumOfIrisRetry());
				}
			}

			LOGGER.info(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					"Enabling scan button for user registration completed");
		} catch (RuntimeException runtimeException) {
			LOGGER.error(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					String.format("%s -> Exception while enabling scan button for user registration  %s %s",
							RegistrationConstants.USER_REG_IRIS_CAPTURE_POPUP_LOAD_EXP, runtimeException.getMessage(),
							ExceptionUtils.getStackTrace(runtimeException)));

			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.UNABLE_LOAD_IRIS_SCAN_POPUP);
		}
	}

	private void updateRetriesBox(double quality, double threshold, int retries) {
		if (quality >= threshold) {
			clearAttemptsBox(RegistrationConstants.QUALITY_LABEL_GREEN, retries);
			irisProgress.getStyleClass().removeAll(RegistrationConstants.PROGRESS_BAR_RED);
			irisProgress.getStyleClass().add(RegistrationConstants.PROGRESS_BAR_GREEN);
			irisQuality.getStyleClass().removeAll(RegistrationConstants.LABEL_RED);
			irisQuality.getStyleClass().add(RegistrationConstants.LABEL_GREEN);
		} else {
			clearAttemptsBox(RegistrationConstants.QUALITY_LABEL_RED, retries);
			irisProgress.getStyleClass().removeAll(RegistrationConstants.PROGRESS_BAR_GREEN);
			irisProgress.getStyleClass().add(RegistrationConstants.PROGRESS_BAR_RED);
			irisQuality.getStyleClass().removeAll(RegistrationConstants.LABEL_GREEN);
			irisQuality.getStyleClass().add(RegistrationConstants.LABEL_RED);
		}
		if (retries > 1) {
			for (int ret = retries; ret > 0; --ret) {
				clearAttemptsBox(RegistrationConstants.QUALITY_LABEL_RED, ret);
			}
		}
	}

	/**
	 * This method displays the Biometric Scan pop-up window. This method will
	 * be invoked when Scan button is clicked.
	 */
	@FXML
	private void scan() {
		try {
			LOGGER.info(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					"Opening pop-up screen to capture Iris for user registration");

			IrisDetailsDTO irisDetailsDTO = getIrisBySelectedPane().findFirst().orElse(null);

			if ((irisDetailsDTO == null || (irisDetailsDTO.getNumOfIrisRetry() < Integer
					.parseInt(getValueFromApplicationContext(RegistrationConstants.IRIS_RETRY_COUNT))))
					|| (irisDetailsDTO == null
							&& ((boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)))) {
				String irisType = (StringUtils.containsIgnoreCase(selectedIris.getId(), RegistrationConstants.LEFT)
						? RegistrationConstants.LEFT
						: RegistrationConstants.RIGHT).toUpperCase();

				auditFactory.audit(AuditEvent.valueOf(String.format("REG_BIO_%s_IRIS_SCAN", irisType)),
						Components.REG_BIOMETRICS, SessionContext.userId(),
						AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

				scanPopUpViewController.init(this, RegistrationUIConstants.IRIS_SCAN);
			}
			// Disable the scan button
			scanIris.setDisable(true);

			LOGGER.info(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					"Opening pop-up screen to capture Iris for user registration completed");
		} catch (RuntimeException runtimeException) {
			LOGGER.error(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					String.format(
							"%s -> Exception while Opening pop-up screen to capture Iris for user registration  %s",
							RegistrationConstants.USER_REG_IRIS_CAPTURE_POPUP_LOAD_EXP,
							ExceptionUtils.getStackTrace(runtimeException)));

			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.UNABLE_LOAD_IRIS_SCAN_POPUP);
		}
	}

	@Override
	public void scan(Stage popupStage) {
		try {
			LOGGER.info(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					"Scanning of iris details for user registration");

			Optional<IrisDetailsDTO> captiredIrisDetailsDTO = getIrisBySelectedPane().findFirst();

			IrisDetailsDTO irisDetailsDTO = null;
			if (!captiredIrisDetailsDTO.isPresent()) {
				irisDetailsDTO = new IrisDetailsDTO();
				getIrises().add(irisDetailsDTO);
			} else {
				irisDetailsDTO = captiredIrisDetailsDTO.get();
			}
			if (!(boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
				irisDetailsDTO.setNumOfIrisRetry(irisDetailsDTO.getNumOfIrisRetry() + 1);
			}

			String irisType = StringUtils.containsIgnoreCase(selectedIris.getId(), RegistrationConstants.LEFT)
					? RegistrationConstants.LEFT
					: RegistrationConstants.RIGHT;

			try {
				bioservice.getIrisImageAsDTO(irisDetailsDTO, irisType.concat(RegistrationConstants.EYE));
			} catch (RegBaseCheckedException runtimeException) {
				LOGGER.error(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID, String.format(
						"%s Exception while getting the scanned iris details for user registration: %s caused by %s",
						RegistrationConstants.USER_REG_IRIS_SAVE_EXP, runtimeException.getMessage(),
						ExceptionUtils.getStackTrace(runtimeException)));
				getIrises().remove(irisDetailsDTO);
				generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.NO_DEVICE_FOUND);
				return;
			}

			if (irisDetailsDTO.getIris() != null) {
				// Display the Scanned Iris Image in the Scan pop-up screen
				scanPopUpViewController.getScanImage().setImage(convertBytesToImage(irisDetailsDTO.getIris()));
				generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.IRIS_SUCCESS_MSG);
				if (irisType.equals(RegistrationConstants.LEFT)) {
					leftIrisImage.setImage(convertBytesToImage(irisDetailsDTO.getIris()));
					leftIrisPane.getStyleClass().add(RegistrationConstants.IRIS_PANES_SELECTED);
					leftIrisQualityScore.setText(getQualityScore(irisDetailsDTO.getQualityScore()));
					leftIrisAttempts.setText(String.valueOf(irisDetailsDTO.getNumOfIrisRetry()));
				} else {
					rightIrisImage.setImage(convertBytesToImage(irisDetailsDTO.getIris()));
					rightIrisPane.getStyleClass().add(RegistrationConstants.IRIS_PANES_SELECTED);
					rightIrisQualityScore.setText(getQualityScore(irisDetailsDTO.getQualityScore()));
					rightIrisAttempts.setText(String.valueOf(irisDetailsDTO.getNumOfIrisRetry()));
				}
				if (!(boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
					irisProgress.setProgress(Double.valueOf(getQualityScore(irisDetailsDTO.getQualityScore())
							.split(RegistrationConstants.PERCENTAGE)[0]) / 100);
					irisQuality.setText(getQualityScore(irisDetailsDTO.getQualityScore()));
					if (Double.valueOf(getQualityScore(irisDetailsDTO.getQualityScore())
							.split(RegistrationConstants.PERCENTAGE)[0]) >= Double
									.valueOf(getValueFromApplicationContext(RegistrationConstants.IRIS_THRESHOLD))) {
						clearAttemptsBox(RegistrationConstants.QUALITY_LABEL_GREEN, irisDetailsDTO.getNumOfIrisRetry());
						irisProgress.getStyleClass().removeAll(RegistrationConstants.PROGRESS_BAR_RED);
						irisProgress.getStyleClass().add(RegistrationConstants.PROGRESS_BAR_GREEN);
						irisQuality.getStyleClass().removeAll(RegistrationConstants.LABEL_RED);
						irisQuality.getStyleClass().add(RegistrationConstants.LABEL_GREEN);
					} else {
						clearAttemptsBox(RegistrationConstants.QUALITY_LABEL_RED, irisDetailsDTO.getNumOfIrisRetry());
						irisProgress.getStyleClass().removeAll(RegistrationConstants.PROGRESS_BAR_GREEN);
						irisProgress.getStyleClass().add(RegistrationConstants.PROGRESS_BAR_RED);
						irisQuality.getStyleClass().removeAll(RegistrationConstants.LABEL_GREEN);
						irisQuality.getStyleClass().add(RegistrationConstants.LABEL_RED);
					}
				}
				popupStage.close();
				if (validateIris() && validateIrisLocalDedup()) {
					continueBtn.setDisable(false);
				}
			} else {
				getIrises().remove(irisDetailsDTO);
				generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.IRIS_SCANNING_ERROR);
			}

			LOGGER.info(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					"Scanning of iris details for user registration completed");
		} catch (RuntimeException runtimeException) {
			LOGGER.error(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID, String.format(
					"%s Exception while getting the scanned iris details for user registration: %s caused by %s",
					RegistrationConstants.USER_REG_IRIS_SAVE_EXP, runtimeException.getMessage(),
					ExceptionUtils.getStackTrace(runtimeException)));

			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.IRIS_SCANNING_ERROR);
		} finally {
			selectedIris.requestFocus();
		}
	}

	private boolean validateIrisLocalDedup() {
		// TODO: Implement Local Dedup for Iris -- Should exculde for Onboard
		// User
		return true;
	}

	/**
	 * This method will be invoked when Next button is clicked. The next section
	 * will be displayed.
	 */
	@FXML
	private void nextSection() {
		try {
			auditFactory.audit(AuditEvent.REG_BIO_IRIS_NEXT, Components.REG_BIOMETRICS, SessionContext.userId(),
					AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

			LOGGER.info(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					"Navigating to Photo capture page for user registration");
			if ((boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
				if (validateIris()) {
					userOnboardParentController.showCurrentPage(RegistrationConstants.IRIS_CAPTURE,
							getOnboardPageDetails(RegistrationConstants.IRIS_CAPTURE, RegistrationConstants.NEXT));
				}
			} else {
				faceCaptureController.disableNextButton();
				rightEyeTrackerImg.setVisible(false);
				leftEyeTrackerImg.setVisible(true);
				if (getRegistrationDTOFromSession().getSelectionListDTO() != null) {

					SessionContext.getInstance().getMapObject().put(RegistrationConstants.UIN_UPDATE_IRISCAPTURE,
							false);

					if (!RegistrationConstants.DISABLE.equalsIgnoreCase(
							getValueFromApplicationContext(RegistrationConstants.FACE_DISABLE_FLAG))) {

						SessionContext.getInstance().getMapObject().put(RegistrationConstants.UIN_UPDATE_FACECAPTURE,
								true);
					} else {
						SessionContext.getInstance().getMapObject()
								.put(RegistrationConstants.UIN_UPDATE_REGISTRATIONPREVIEW, true);
						registrationPreviewController.setUpPreviewContent();
					}
					registrationController.showUINUpdateCurrentPage();
				} else {
					registrationController.showCurrentPage(RegistrationConstants.IRIS_CAPTURE,
							getPageDetails(RegistrationConstants.IRIS_CAPTURE, RegistrationConstants.NEXT));
				}
			}

			LOGGER.info(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					"Navigating to Photo capture page for user registration completed");
		} catch (RuntimeException runtimeException) {
			LOGGER.error(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					String.format("%s -> Exception while navigating to Photo capture page for user registration  %s",
							RegistrationConstants.USER_REG_IRIS_CAPTURE_NEXT_SECTION_LOAD_EXP,
							ExceptionUtils.getStackTrace(runtimeException)));

			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.IRIS_NAVIGATE_NEXT_SECTION_ERROR);
		}
	}

	/**
	 * This method will be invoked when Previous button is clicked. The previous
	 * section will be displayed.
	 */
	@FXML
	private void previousSection() {
		try {
			auditFactory.audit(AuditEvent.REG_BIO_IRIS_BACK, Components.REG_BIOMETRICS, SessionContext.userId(),
					AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

			LOGGER.info(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					"Navigating to Fingerprint capture page for user registration");

			if ((boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
				userOnboardParentController.showCurrentPage(RegistrationConstants.IRIS_CAPTURE,
						getOnboardPageDetails(RegistrationConstants.IRIS_CAPTURE, RegistrationConstants.PREVIOUS));
			} else {
				if (getRegistrationDTOFromSession().getSelectionListDTO() != null) {

					SessionContext.map().put(RegistrationConstants.UIN_UPDATE_IRISCAPTURE, false);

					if (RegistrationConstants.ENABLE.equalsIgnoreCase(
							getValueFromApplicationContext(RegistrationConstants.FINGERPRINT_DISABLE_FLAG))) {
						SessionContext.map().put(RegistrationConstants.UIN_UPDATE_FINGERPRINTCAPTURE, true);
					} else {
						SessionContext.map().put(RegistrationConstants.UIN_UPDATE_DOCUMENTSCAN, true);
					}
					registrationController.showUINUpdateCurrentPage();
				} else {
					registrationController.showCurrentPage(RegistrationConstants.IRIS_CAPTURE,
							getPageDetails(RegistrationConstants.IRIS_CAPTURE, RegistrationConstants.PREVIOUS));
				}
			}

			LOGGER.debug(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					"Navigating to Fingerprint capture page for user registration completed");
		} catch (RuntimeException runtimeException) {
			LOGGER.error(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					String.format(
							"%s -> Exception while navigating to Fingerprint capture page for user registration  %s",
							RegistrationConstants.USER_REG_IRIS_CAPTURE_PREV_SECTION_LOAD_EXP,
							ExceptionUtils.getStackTrace(runtimeException)));

			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.IRIS_NAVIGATE_PREVIOUS_SECTION_ERROR);
		}
	}

	/**
	 * Validate iris.
	 *
	 * @return true, if successful
	 */
	private boolean validateIris() {
		try {
			LOGGER.info(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					"Validating the captured irises of individual");

			boolean isValid = false;
			boolean isLeftEyeCaptured = false;
			boolean isRightEyeCaptured = false;

			for (BiometricExceptionDTO exceptionIris : getIrisExceptions()) {
				if (exceptionIris.getMissingBiometric()
						.equalsIgnoreCase(RegistrationConstants.LEFT.concat(RegistrationConstants.EYE))) {
					isLeftEyeCaptured = true;
				} else if (exceptionIris.getMissingBiometric()
						.equalsIgnoreCase(RegistrationConstants.RIGHT.concat(RegistrationConstants.EYE))) {
					isRightEyeCaptured = true;
				}
			}

			for (IrisDetailsDTO irisDetailsDTO : getIrises()) {
				if (validateIrisCapture(irisDetailsDTO)
						|| (boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
					if (irisDetailsDTO.getIrisType()
							.equalsIgnoreCase(RegistrationConstants.LEFT.concat(RegistrationConstants.EYE))) {
						isLeftEyeCaptured = true;
					} else if (irisDetailsDTO.getIrisType()
							.equalsIgnoreCase(RegistrationConstants.RIGHT.concat(RegistrationConstants.EYE))) {
						isRightEyeCaptured = true;
					}
				} else {
					return isValid;
				}
			}

			if (getRegistrationDTOFromSession() != null && getRegistrationDTOFromSession().getSelectionListDTO() != null

					&& ((getRegistrationDTOFromSession().getSelectionListDTO().isBiometrics() && isLeftEyeCaptured
							&& isRightEyeCaptured)

							|| getRegistrationDTOFromSession().getBiometricDTO().getIntroducerBiometricDTO()
									.getFingerprintDetailsDTO().isEmpty()
									&& !getRegistrationDTOFromSession().getSelectionListDTO().isBiometrics()
									&& (isLeftEyeCaptured || isRightEyeCaptured))) {
				isValid = true;
			} else {
				if (isLeftEyeCaptured && isRightEyeCaptured) {
					isValid = true;
				}
			}

			LOGGER.info(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					"Validating the captured irises of individual completed");

			return isValid;
		} catch (RuntimeException runtimeException) {
			throw new RegBaseUncheckedException(RegistrationConstants.USER_REG_IRIS_VALIDATION_EXP,
					String.format("Exception while validating the captured irises of individual: %s caused by %s",
							runtimeException.getMessage(), ExceptionUtils.getStackTrace(runtimeException)));
		}
	}

	private boolean validateIrisCapture(IrisDetailsDTO irisDetailsDTO) {
		try {
			LOGGER.info(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					"Validating the quality score of the captured iris");

			// Get Configured Threshold and Number of Retries from properties
			// file
			double irisThreshold = Double
					.parseDouble(getValueFromApplicationContext(RegistrationConstants.IRIS_THRESHOLD));
			int numOfRetries = Integer.parseInt(getValueFromApplicationContext(RegistrationConstants.IRIS_RETRY_COUNT));

			LOGGER.info(LOG_REG_IRIS_CAPTURE_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					"Validating the quality score of the captured iris completed");

			return irisDetailsDTO.getQualityScore() >= irisThreshold
					|| (Double.compare(irisDetailsDTO.getQualityScore(), irisThreshold) < 0
							&& irisDetailsDTO.getNumOfIrisRetry() == numOfRetries)
					|| irisDetailsDTO.isForceCaptured();
		} catch (RuntimeException runtimeException) {
			throw new RegBaseUncheckedException(RegistrationConstants.USER_REG_IRIS_SCORE_VALIDATION_EXP,
					String.format("Exception while validating the quality score of captured iris: %s caused by %s",
							runtimeException.getMessage(), ExceptionUtils.getStackTrace(runtimeException)));
		}
	}

	private List<IrisDetailsDTO> getIrises() {
		if ((boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
			return getBiometricDTOFromSession().getOperatorBiometricDTO().getIrisDetailsDTO();
		} else if (getRegistrationDTOFromSession().isUpdateUINChild()) {
			return getRegistrationDTOFromSession().getBiometricDTO().getIntroducerBiometricDTO().getIrisDetailsDTO();
		} else {
			return getRegistrationDTOFromSession().getBiometricDTO().getApplicantBiometricDTO().getIrisDetailsDTO();
		}
	}

	private Stream<IrisDetailsDTO> getIrisBySelectedPane() {
		return getIrises().stream().filter(iris -> iris.getIrisType().contains(getSelectedIris()));
	}

	private String getSelectedIris() {
		return StringUtils.containsIgnoreCase(selectedIris.getId(), RegistrationConstants.LEFT)
				? RegistrationConstants.LEFT
				: RegistrationConstants.RIGHT;
	}

	public void clearIrisData() {
		leftIrisImage
				.setImage(new Image(getClass().getResource(RegistrationConstants.LEFT_IRIS_IMG_PATH).toExternalForm()));
		leftIrisQualityScore.setText(RegistrationConstants.EMPTY);
		leftIrisAttempts.setText(RegistrationConstants.HYPHEN);

		rightIrisImage.setImage(
				new Image(getClass().getResource(RegistrationConstants.RIGHT_IRIS_IMG_PATH).toExternalForm()));
		rightIrisQualityScore.setText(RegistrationConstants.EMPTY);
		rightIrisAttempts.setText(RegistrationConstants.HYPHEN);
		clearProgressBar();

		if (getRegistrationDTOFromSession() != null) {
			if (getRegistrationDTOFromSession().isUpdateUINChild()) {
				getRegistrationDTOFromSession().getBiometricDTO().getIntroducerBiometricDTO()
						.setIrisDetailsDTO(new ArrayList<>());
			} else {
				getRegistrationDTOFromSession().getBiometricDTO().getApplicantBiometricDTO()
						.setIrisDetailsDTO(new ArrayList<>());
			}
		}

		singleBiometricCaptureCheck();
	}

	private void clearProgressBar() {
		if (!(boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {

			irisProgress.setProgress(0);
			irisProgress.getStyleClass().removeAll(RegistrationConstants.PROGRESS_BAR_RED);
			irisProgress.getStyleClass().remove(RegistrationConstants.PROGRESS_BAR_GREEN);

			irisQuality.setText(RegistrationConstants.EMPTY);
			irisQuality.getStyleClass().remove(RegistrationConstants.LABEL_RED);
			irisQuality.getStyleClass().remove(RegistrationConstants.LABEL_GREEN);

			for (int attempt = 0; attempt < Integer
					.parseInt(getValueFromApplicationContext(RegistrationConstants.IRIS_RETRY_COUNT)); attempt++) {
				irisRetryBox.lookup(RegistrationConstants.RETRY_ATTEMPT + (attempt + 1)).getStyleClass().clear();
				irisRetryBox.lookup(RegistrationConstants.RETRY_ATTEMPT + (attempt + 1)).getStyleClass()
						.add(RegistrationConstants.QUALITY_LABEL_GREY);
			}
		}
	}

	public void clearIrisBasedOnExceptions() {
		if (anyIrisException(RegistrationConstants.LEFT)) {
			leftIrisImage.setImage(
					new Image(getClass().getResource(RegistrationConstants.LEFT_IRIS_IMG_PATH).toExternalForm()));
			leftIrisQualityScore.setText(RegistrationConstants.EMPTY);
			leftIrisAttempts.setText(RegistrationConstants.HYPHEN);
			clearProgressBar();
			getIrises().removeIf(iris -> iris.getIrisType()
					.equalsIgnoreCase((RegistrationConstants.LEFT).concat(RegistrationConstants.EYE)));
		}

		if (anyIrisException(RegistrationConstants.RIGHT)) {
			rightIrisImage.setImage(
					new Image(getClass().getResource(RegistrationConstants.RIGHT_IRIS_IMG_PATH).toExternalForm()));
			rightIrisQualityScore.setText(RegistrationConstants.EMPTY);
			rightIrisAttempts.setText(RegistrationConstants.HYPHEN);
			clearProgressBar();
			getIrises().removeIf(iris -> iris.getIrisType()
					.equalsIgnoreCase((RegistrationConstants.RIGHT).concat(RegistrationConstants.EYE)));
		}

		if (anyIrisException(RegistrationConstants.LEFT) && anyIrisException(RegistrationConstants.RIGHT)) {
			continueBtn.setDisable(false);
		}

		populateException();
	}

	private void singleBiometricCaptureCheck() {
		if (!(validateIris() && validateIrisLocalDedup())) {
			continueBtn.setDisable(true);
		}
		if (getRegistrationDTOFromSession() != null
				&& !getRegistrationDTOFromSession().getBiometricDTO().getIntroducerBiometricDTO()
						.getFingerprintDetailsDTO().isEmpty()
				&& getRegistrationDTOFromSession().getSelectionListDTO() != null
				&& !getRegistrationDTOFromSession().getSelectionListDTO().isBiometrics()) {
			continueBtn.setDisable(false);
		}
	}

	private void clearAttemptsBox(String styleClass, int retries) {
		irisRetryBox.lookup(RegistrationConstants.RETRY_ATTEMPT + (retries)).getStyleClass().clear();
		irisRetryBox.lookup(RegistrationConstants.RETRY_ATTEMPT + (retries)).getStyleClass().add(styleClass);
	}
}
