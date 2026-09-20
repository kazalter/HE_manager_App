import SwiftUI

public struct LoginView: View {
    @State private var viewModel = LoginViewModel()
    @Bindable var preferences = Preferences.shared
    public let onLoggedIn: () -> Void

    public init(onLoggedIn: @escaping () -> Void) {
        self.onLoggedIn = onLoggedIn
    }

    public var body: some View {
        ZStack {
            OPTheme.void
                .ignoresSafeArea()

            // Cyber background lines
            VStack {
                Rectangle()
                    .fill(OPTheme.yellow)
                    .frame(height: 2)
                Spacer()
            }
            .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 24) {
                    // Logo / Header
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 8) {
                            Rectangle()
                                .fill(OPTheme.yellow)
                                .frame(width: 8, height: 24)
                            Text("HE MANAGER")
                                .font(.system(size: 24, weight: .heavy, design: .monospaced))
                                .foregroundColor(OPTheme.opWhite)
                                .tracking(2.0)
                        }

                        Text("// OPERATOR LINK TERMINAL v1.0")
                            .font(.system(size: 11, weight: .medium, design: .monospaced))
                            .foregroundColor(OPTheme.yellowDim)
                            .tracking(1.0)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, 40)

                    // Server Section
                    VStack(alignment: .leading, spacing: 10) {
                        Text("SERVER ENDPOINT")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundColor(OPTheme.opWhiteMuted)

                        AngularPanel(cut: 8, hairline: true) {
                            HStack {
                                Image(systemName: "server.rack")
                                    .foregroundColor(OPTheme.yellow)
                                    .font(.system(size: 14))

                                TextField("http://192.168.x.x:8010", text: $viewModel.server)
                                    .font(.system(size: 14, design: .monospaced))
                                    .foregroundColor(OPTheme.opWhite)
                                    .autocorrectionDisabled()
                                    .textInputAutocapitalization(.never)
                                    .keyboardType(.URL)
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                        }

                        // Server History Chips
                        if !preferences.serverHistory.isEmpty {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 8) {
                                    ForEach(preferences.serverHistory, id: \.self) { s in
                                        Button {
                                            viewModel.selectServer(s)
                                        } label: {
                                            HStack(spacing: 4) {
                                                Text(s)
                                                    .font(.system(size: 11, design: .monospaced))
                                                if s == viewModel.server {
                                                    Image(systemName: "checkmark")
                                                        .font(.system(size: 9, weight: .bold))
                                                }
                                            }
                                            .foregroundColor(s == viewModel.server ? OPTheme.onYellow : OPTheme.opWhiteSoft)
                                            .padding(.horizontal, 10)
                                            .padding(.vertical, 5)
                                            .background(
                                                s == viewModel.server ? OPTheme.yellow : OPTheme.panel
                                            )
                                            .clipShape(RoundedRectangle(cornerRadius: 4))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Credentials Section
                    VStack(alignment: .leading, spacing: 14) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("OPERATOR ID")
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                .foregroundColor(OPTheme.opWhiteMuted)

                            AngularPanel(cut: 8, hairline: true) {
                                HStack {
                                    Image(systemName: "person")
                                        .foregroundColor(OPTheme.yellow)
                                        .font(.system(size: 14))

                                    TextField("Username", text: $viewModel.username)
                                        .font(.system(size: 14, design: .monospaced))
                                        .foregroundColor(OPTheme.opWhite)
                                        .autocorrectionDisabled()
                                        .textInputAutocapitalization(.never)
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 12)
                            }
                        }

                        VStack(alignment: .leading, spacing: 6) {
                            Text("ACCESS KEY")
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                .foregroundColor(OPTheme.opWhiteMuted)

                            AngularPanel(cut: 8, hairline: true) {
                                HStack {
                                    Image(systemName: "key")
                                        .foregroundColor(OPTheme.yellow)
                                        .font(.system(size: 14))

                                    SecureField("Password", text: $viewModel.password)
                                        .font(.system(size: 14, design: .monospaced))
                                        .foregroundColor(OPTheme.opWhite)
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 12)
                            }
                        }
                    }

                    // Mode Toggle (Login vs First-time Bootstrap)
                    Toggle(isOn: $viewModel.isBootstrap) {
                        Text("INITIALIZE ROOT ADMIN")
                            .font(.system(size: 12, weight: .medium, design: .monospaced))
                            .foregroundColor(OPTheme.opWhiteSoft)
                    }
                    .toggleStyle(SwitchToggleStyle(tint: OPTheme.yellow))

                    // Status terminal message
                    if !viewModel.message.isEmpty {
                        Text(viewModel.message)
                            .font(.system(size: 12, weight: .medium, design: .monospaced))
                            .foregroundColor(viewModel.message.contains("ERR") ? OPTheme.danger : OPTheme.cyan)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(10)
                            .background(OPTheme.panel)
                            .clipShape(RoundedRectangle(cornerRadius: 4))
                    }

                    // Connect CTA
                    YellowCTA(
                        title: viewModel.isBootstrap ? "CREATE ROOT ACCOUNT" : "CONNECT TERMINAL",
                        icon: viewModel.isBusy ? nil : "arrow.right.circle.fill",
                        size: .large,
                        fullWidth: true
                    ) {
                        viewModel.submit(onSuccess: onLoggedIn)
                    }
                    .disabled(viewModel.isBusy)
                    .opacity(viewModel.isBusy ? 0.6 : 1.0)

                    if viewModel.isBusy {
                        ProgressView()
                            .tint(OPTheme.yellow)
                    }
                }
                .padding(24)
            }
        }
    }
}
