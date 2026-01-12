import SwiftUI
import AVFoundation
import ComposeApp

struct PulseCameraView: View {
    @StateObject private var viewModel = PulseViewModel()
    @State private var cameraAuthorized = false
    @AppStorage("has_seen_intro") private var hasSeenIntro = false
    @State private var showInfo = false

    var body: some View {
        Group {
            if hasSeenIntro {
                ZStack {
                    if cameraAuthorized {
                        CameraPreview(position: .back)
                    } else {
                        Color.black
                    }
                    ClusterDots(dots: viewModel.dots) { width, height in
                        viewModel.updateViewport(width: Float(width), height: Float(height))
                    }
                    VStack {
                        HStack {
                            Spacer()
                            InfoButton {
                                showInfo = true
                            }
                        }
                        Spacer()
                    }
                    .padding(.top, 32)
                    .padding(.trailing, 16)
                }
                .onAppear {
                    requestCameraAccess()
                }
                .sheet(isPresented: $showInfo) {
                    InfoSheet(summary: viewModel.summary, debug: viewModel.debug)
                }
            } else {
                IntroView {
                    hasSeenIntro = true
                    requestCameraAccess()
                }
            }
        }
    }

    private func requestCameraAccess() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            cameraAuthorized = true
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    cameraAuthorized = granted
                }
            }
        default:
            cameraAuthorized = false
        }
    }
}

struct ClusterDots: View {
    let dots: [UiDot]
    let onViewport: (CGFloat, CGFloat) -> Void

    var body: some View {
        GeometryReader { proxy in
            Color.clear
                .onAppear {
                    onViewport(proxy.size.width, proxy.size.height)
                }
                .onChange(of: proxy.size) { size in
                    onViewport(size.width, size.height)
                }
            ForEach(dots, id: \.key) { dot in
                PulsingDot()
                    .position(x: CGFloat(dot.screenX), y: CGFloat(dot.screenY))
            }
        }
        .allowsHitTesting(false)
    }
}

struct PulsingDot: View {
    @State private var pulse = false

    var body: some View {
        Circle()
            .fill(Color.red.opacity(0.8))
            .frame(width: pulse ? 20 : 10, height: pulse ? 20 : 10)
            .overlay(
                Circle()
                    .stroke(Color.red.opacity(0.6), lineWidth: 2)
                    .frame(width: pulse ? 36 : 20, height: pulse ? 36 : 20)
                    .opacity(pulse ? 0.2 : 0.8)
            )
            .onAppear {
                withAnimation(
                    Animation.easeInOut(duration: 1.2)
                        .repeatForever(autoreverses: true)
                ) {
                    pulse.toggle()
                }
            }
    }
}

struct IntroView: View {
    let onStart: () -> Void
    @State private var pulse = false

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color.black, Color(red: 0.08, green: 0.08, blue: 0.1)],
                startPoint: .top,
                endPoint: .bottom
            )
            VStack(spacing: 18) {
                ZStack {
                    Circle()
                        .fill(Color.red.opacity(0.7))
                        .frame(width: pulse ? 70 : 50, height: pulse ? 70 : 50)
                    Circle()
                        .stroke(Color.red.opacity(0.35), lineWidth: 3)
                        .frame(width: pulse ? 110 : 80, height: pulse ? 110 : 80)
                }
                .onAppear {
                    withAnimation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true)) {
                        pulse.toggle()
                    }
                }
                Text("Passive signal sensing. No identities.")
                    .font(.body)
                    .foregroundColor(.white.opacity(0.85))
                Button(action: onStart) {
                    Text("Start scanning")
                        .font(.headline)
                        .foregroundColor(.black)
                        .padding(.vertical, 12)
                        .padding(.horizontal, 28)
                        .background(Color.white)
                        .clipShape(Capsule())
                }
            }
        }
        .ignoresSafeArea()
    }
}

struct InfoButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "info")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white)
                .frame(width: 36, height: 36)
                .background(BlurView(style: .systemUltraThinMaterialDark))
                .clipShape(Circle())
                .overlay(
                    Circle().stroke(Color.white.opacity(0.4), lineWidth: 1)
                )
        }
    }
}

struct InfoSheet: View {
    let summary: TrackerSummary?
    let debug: DebugSnapshot?

    var body: some View {
        let totalDevices = Int(summary?.totalDevices ?? 0)
        let confidence: String = {
            guard let level = summary?.confidenceLevel else { return "Low" }
            switch level {
            case .high:
                return "High"
            case .medium:
                return "Medium"
            default:
                return "Low"
            }
        }()
        let stationaryCount = Int(summary?.stationaryCount ?? 0)

        VStack(alignment: .leading, spacing: 12) {
            Text("We found \(totalDevices) devices around you.")
                .font(.title3.weight(.semibold))
            HStack(spacing: 12) {
                Text("Confidence: \(confidence)")
                Text("Stationary: \(stationaryCount)")
            }
            .font(.subheadline)
            .foregroundColor(.secondary)
            Text("Passive Bluetooth signals only. No identities stored.")
                .font(.footnote)
                .foregroundColor(.secondary)
            if let debug {
                Text("Tracks: \(debug.totalTracks) • Dots: \(debug.trackableCount)")
                    .font(.footnote)
                    .foregroundColor(.secondary)
                ForEach(debug.topDevices, id: \.keyPrefix) { device in
                    Text(
                        "\(device.keyPrefix)  rssi=\(String(format: "%.1f", device.rssiEma))  " +
                            "phone=\(String(format: "%.2f", device.phoneScore))  " +
                            "conf=\(String(format: "%.2f", device.confidence))  " +
                            "dt=\(device.lastSeenDeltaMs)ms"
                    )
                    .font(.footnote)
                    .foregroundColor(.secondary)
                }
            }
            Spacer()
        }
        .padding(20)
        .presentationDetents([.medium])
    }
}

struct BlurView: UIViewRepresentable {
    let style: UIBlurEffect.Style

    func makeUIView(context: Context) -> UIVisualEffectView {
        UIVisualEffectView(effect: UIBlurEffect(style: style))
    }

    func updateUIView(_ uiView: UIVisualEffectView, context: Context) {}
}


struct CameraPreview: UIViewRepresentable {
    let position: AVCaptureDevice.Position

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UIView {
        let view = PreviewView(frame: .zero)
        context.coordinator.controller.attachPreview(to: view)
        context.coordinator.controller.start(position: position)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.controller.updateFrame(for: uiView)
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.controller.stop()
    }

    final class Coordinator {
        let controller = CameraSessionController()
    }
}

final class CameraSessionController {
    private let session = AVCaptureSession()
    private let previewLayer = AVCaptureVideoPreviewLayer()
    private let sessionQueue = DispatchQueue(label: "pulse.camera.session")
    private var configured = false

    func attachPreview(to view: UIView) {
        previewLayer.videoGravity = .resizeAspectFill
        previewLayer.session = session
        view.layer.addSublayer(previewLayer)
        previewLayer.frame = view.bounds
        previewLayer.needsDisplayOnBoundsChange = true
    }

    func updateFrame(for view: UIView) {
        previewLayer.frame = view.bounds
    }

    func start(position: AVCaptureDevice.Position) {
        sessionQueue.async {
            if !self.configured {
                self.configureSession(position: position)
            }
            if !self.session.isRunning {
                self.session.startRunning()
            }
        }
    }

    func stop() {
        sessionQueue.async {
            if self.session.isRunning {
                self.session.stopRunning()
            }
        }
    }

    private func configureSession(position: AVCaptureDevice.Position) {
        session.beginConfiguration()
        session.sessionPreset = .high
        session.inputs.forEach { session.removeInput($0) }
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            session.commitConfiguration()
            return
        }
        session.addInput(input)
        if let connection = previewLayer.connection, connection.isVideoOrientationSupported {
            connection.videoOrientation = .portrait
        }
        session.commitConfiguration()
        configured = true
    }
}

final class PreviewView: UIView {
    override func layoutSubviews() {
        super.layoutSubviews()
        layer.sublayers?.forEach { layer in
            layer.frame = bounds
        }
    }
}
